package dev.vitrail.glsl;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkFadeShaderTest {
	@Test
	void displacesSectionLocalPositionBeforeRegionTranslation() {
		String source = "#version 460\n// ofLocal = fake;\nvoid ofSodiumVertex() {\n"
				+ "vec3 ofLocal = decodePosition();\nof_Vertex = vec4(ofLocal + of_RegionOffset, 1.0);\n}\n";
		String patched = ChunkFadeShader.vertex(source, "uniform samplerBuffer cfi_u_FadeData;", "ofLocal += chunkFadeData.xyz;");
		assertTrue(patched.indexOf("uniform samplerBuffer") > patched.indexOf("#version"));
		assertTrue(patched.indexOf("ofLocal += chunkFadeData.xyz;") > patched.indexOf("decodePosition();"));
		assertTrue(patched.indexOf("ofLocal += chunkFadeData.xyz;") < patched.indexOf("of_Vertex ="));
	}

	@Test
	void fadesAfterAnEarlyReturnAndKeepsTheTranslatedAlphaTest() {
		String source = "#version 460\nvoid main() { if (flag) return; if (alpha < 0.1) discard; }\n";
		String patched = ChunkFadeShader.fragment(source, "flat in float cfi_FadeFactor;", "applyFade();");
		assertTrue(patched.contains("#define main ofCfiMain"));
		assertTrue(patched.contains("if (alpha < 0.1) discard;"));
		assertTrue(patched.contains("ofCfiMain();\napplyFade();"));
	}

	@Test
	void refusesAnUnknownPrologueInsteadOfSilentlyClaimingSupport() {
		assertThrows(IllegalArgumentException.class, () -> ChunkFadeShader.vertex("void main() {}", "", "init();"));
	}
	@Test
	void wrapsBothConditionalEntryPointsAndTheirPrototype() {
		String source = "#version 460\nvoid main();\n#if MODE\nvoid main() {}\n#else\nvoid main() {}\n#endif\n";
		String patched = ChunkFadeShader.fragment(source, "flat in float cfi_FadeFactor;", "applyFade();");
		assertTrue(patched.indexOf("#define main ofCfiMain") < patched.indexOf("void main();"));
		assertTrue(patched.indexOf("#undef main") > patched.indexOf("#endif"));
	}

	@Test
	void preservesExtensionsBeforeGeneratedDeclarations() {
		String source = "#version 460\n#extension GL_EXT_shader_explicit_arithmetic_types_float16 : require\n"
				+ "layout(location = 0) out vec4 colour;\nvoid main() { colour = vec4(float16_t(1.0)); }\n";
		String patched = ChunkFadeShader.fragment(source, "flat in float cfi_FadeFactor;", "colour.rgb *= cfi_FadeFactor;");
		assertTrue(patched.indexOf("#extension") < patched.indexOf("flat in float"));
		assertTrue(patched.indexOf(": require\n") < patched.indexOf("flat in float"));
	}

	private static String fade(String source) {
		return ChunkFadeShader.fade(source, "flat in float cfi_FadeFactor;",
				(colour, channels) -> "FADE(" + colour + ", sky." + channels + ");\n",
				"ofCfiColour *= cfi_FadeFactor;");
	}

	@Test
	void fadesScalarOutputAfterEarlyReturns() {
		String result = fade("#version 460\nlayout(location = 0) out float light;\n"
				+ "void main() { if (skip) return; light = 1.0; }\n");
		assertTrue(result.contains("ofCfiMain();\nlight *= cfi_FadeFactor;"));
	}

	@Test
	void fadesTemporaryColourBeforeEachPackingWithoutChangingSource() {
		String source = "#version 460\nlayout(location = 0) out uvec4 packed;\n"
				+ "void main() { vec3 color = vec3(1.0);\n"
				+ "packed.x = packUnorm2x16(color.rg);\n"
				+ "packed.y = packUnorm2x16(vec2(color.b, 0.0)); }\n";
		String result = fade(source);
		assertTrue(result.indexOf("FADE(ofCfiPacked0.rgb, sky.rgb)") > result.indexOf("vec3 color ="));
		assertTrue(result.indexOf("FADE(ofCfiPacked0.rgb, sky.rgb)") < result.indexOf("packed.x ="));
		assertEquals(2, result.split("FADE", -1).length - 1);
		assertFalse(result.contains("FADE(packed"));
	}

	@Test
	void keepsPackingLocalToEachBlock() {
		String source = "#version 460\nlayout(location = 0) out uvec4 target;\nvoid main() {\n"
				+ "if (mode) { vec3 color = vec3(1.0); target.x = packUnorm4x8(vec4(color.rgb, 1.0)); }\n"
				+ "else { vec3 color = vec3(0.0); target.x = packUnorm4x8(vec4(color.rgb, 1.0)); } }\n";
		String result = fade(source);
		assertEquals(2, result.split("FADE", -1).length - 1);
		assertTrue(result.indexOf("FADE(ofCfiPacked0.rgb") > result.indexOf("vec3 color = vec3(1.0)"));
		assertTrue(result.lastIndexOf("FADE(ofCfiPacked1.rgb") > result.indexOf("vec3 color = vec3(0.0)"));
	}

	@Test
	void refusesUnknownIntegerPackingWithoutFadingEncodedValues() {
		assertThrows(IllegalArgumentException.class, () -> fade("#version 460\n"
				+ "layout(location = 0) out uint material;\nvoid main() { material = encode(data); }\n"));
	}

	@Test
	void choosesLowestOutputLocationAndIgnoresComments() {
		String result = fade("#version 460\n// layout(location = 0) out vec4 fake;\n"
				+ "layout(location = 2) out vec4 material;\nlayout(location = 1) out vec3 colour;\n"
				+ "void main() { colour = vec3(1.0); }\n");
		assertTrue(result.contains("FADE(colour, sky.rgb)"));
		assertFalse(result.contains("FADE(material"));
		assertFalse(result.contains("FADE(fake"));
	}

	@Test
	void carriesOnlyLiveOptOutDefinesAndIgnoresCommentsWhenReadingMarkers() {
		String result = ChunkFadeShader.preserveMarkers("#version 460\nvoid main() {}\n",
				java.util.Map.of("CHUNKS_FADE_IN_NO_FRAG_MOD_INJECT", "1"));
		assertTrue(ChunkFadeShader.marker(result, "_cfi_noInjectFragModMarker"));
		assertFalse(ChunkFadeShader.marker(result, "_cfi_noInjectVertModMarker"));
		assertFalse(ChunkFadeShader.marker("// int _cfi_noInjectMarker;", "_cfi_noInjectMarker"));
	}

	@Test
	void rereadsColourAfterReassignmentAndPreservesPackedAlpha() {
		String source = "#version 460\nlayout(location = 0) out uvec4 target;\n"
				+ "void main() { vec4 color = vec4(1.0); target.x = packUnorm4x8(color.rgba);\n"
				+ "color = vec4(0.5); target.y = packUnorm4x8(color.rgba); float later = color.a; }\n";
		String result = fade(source);
		assertTrue(result.contains("vec4 ofCfiPacked0 = color;"));
		assertTrue(result.contains("vec4 ofCfiPacked1 = color;"));
		assertTrue(result.indexOf("vec4 ofCfiPacked1 = color;") > result.indexOf("color = vec4(0.5)"));
		assertTrue(result.contains("FADE(ofCfiPacked0.rgb, sky.rgb)"));
		assertFalse(result.contains("FADE(color"));
		assertFalse(result.contains("FADE(ofCfiPacked0.rgba"));
		assertTrue(result.contains("float later = color.a;"));
	}

	@Test
	void refusesUnbracedPackingInsteadOfMixingEncodedFloatingOutput() {
		assertThrows(IllegalArgumentException.class, () -> fade("#version 460\n"
				+ "layout(location = 0) out vec4 target;\nvoid main() { vec3 color = vec3(1.0);\n"
				+ "if (mode) target.x = uintBitsToFloat(packUnorm2x16(color.rg)); }\n"));
	}

	@Test
	void refusesIndexedPackedWritesAndKeepsTypesOutOfClosedScopes() {
		assertThrows(IllegalArgumentException.class, () -> fade("#version 460\n"
				+ "layout(location = 0) out vec4 target;\nvoid main() { vec3 color = vec3(1.0);\n"
				+ "target[0] = uintBitsToFloat(packUnorm2x16(color.rg)); }\n"));
		String result = fade("#version 460\nlayout(location = 0) out uvec4 target;\n"
				+ "vec4 color; void main() { {vec2 color=vec2(0.5);}\n"
				+ "target.x=packUnorm4x8(color.rgba); }\n");
		assertTrue(result.contains("vec4 ofCfiPacked0 = color;"));
	}

	@Test
	void terrainTranslationKeepsLiveOptOutAndDropsInactiveOptOut(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)
			throws java.io.IOException {
		java.nio.file.Path shaders = java.nio.file.Files.createDirectories(root.resolve("shaders"));
		java.nio.file.Path vertex = shaders.resolve("gbuffers_terrain.vsh");
		java.nio.file.Files.writeString(vertex, "#version 120\n"
				+ "#if 0\n#define CHUNKS_FADE_IN_NO_VERT_MOD_INJECT\n#endif\n"
				+ "#define CHUNKS_FADE_IN_NO_FRAG_MOD_INJECT\nvoid main() {gl_Position=ftransform();}\n");
		try (var pack = dev.vitrail.pack.source.ShaderPackSource.open(root)) {
			var settings = dev.vitrail.pack.option.SettingSet.resolve(java.util.Map.of(), java.util.Map.of(), "test");
			var unit = new dev.vitrail.pack.source.IncludeExpander(pack, settings).expand(vertex);
			var translated = ProgramTranslator.translate(java.util.Map.of(
					dev.vitrail.pack.model.ProgramStage.VERTEX, unit), VertexInputs.TERRAIN, "gbuffers_terrain");
			String text = translated.stages().get(dev.vitrail.pack.model.ProgramStage.VERTEX).text();
			assertTrue(ChunkFadeShader.marker(text, "_cfi_noInjectFragModMarker"));
			assertFalse(ChunkFadeShader.marker(text, "_cfi_noInjectVertModMarker"));
		}
	}

	@Test
	void refusesArithmeticAndFunctionCallsBeforePacking() {
		for (String argument : java.util.List.of("-color.rg", "color.rg - color.rg", "colorize().rg")) {
			assertThrows(IllegalArgumentException.class, () -> fade("#version 460\n"
					+ "layout(location = 0) out uvec4 target;\nvec3 colorize(){return vec3(1.0);}\n"
					+ "void main() {vec3 color=vec3(1.0);target.x=packUnorm2x16(" + argument + ");}\n"));
		}
	}

	@Test
	void doesNotUseAnotherFunctionsParameterTypeOrShadowPackTemporaryNames() {
		String result = fade("#version 460\nlayout(location = 0) out uvec4 target;\n"
				+ "vec4 color;vec3 helper(vec2 color){return vec3(color,0.0); }\n"
				+ "void main(){float ofCfiPacked0=1.0;target.x=packUnorm4x8(color.rgba); }\n");
		assertTrue(result.contains("vec4 ofCfiPacked1 = color;"));
		assertFalse(result.contains("vec2 ofCfiPacked1 = color;"));
		assertTrue(result.contains("float ofCfiPacked0=1.0;"));
	}

	@Test
	void refusesLoopAndStructColoursThatShadowKnownVectorTypes() {
		String prefix = "#version 460\nlayout(location=0)out uvec4 target;vec4 color;\n";
		assertThrows(IllegalArgumentException.class, () -> fade(prefix
				+ "void main(){for(vec2 color=vec2(1.0);true;){target.x=packUnorm2x16(color.rg);}}\n"));
		assertThrows(IllegalArgumentException.class, () -> fade(prefix
				+ "struct Data{vec2 rg;};void main(){Data color;target.x=packUnorm2x16(color.rg);}\n"));
	}

	@Test
	void refusesTypesThatDependOnAnUnresolvedPreprocessorBranch() {
		assertThrows(IllegalArgumentException.class, () -> fade("#version 460\n"
				+ "layout(location=0)out uvec4 target;void main(){\n"
				+ "#if MODE\nvec4 color;\n#else\nvec3 color;\n#endif\n"
				+ "target.x=packUnorm2x16(color.rg); }\n"));
	}

	@Test
	void refusesEncodedFloatingOutputsPassedThroughLocals() {
		for (String conversion : java.util.List.of("uintBitsToFloat", "intBitsToFloat")) {
			String type = conversion.startsWith("uint") ? "uint" : "int";
			String packed = type + "(packUnorm2x16(color.rg))";
			for (String output : java.util.List.of("float", "vec2", "vec3", "vec4")) {
				String prefix = "#version 460\nlayout(location=0) out " + output + " target;\n"
						+ "void main(){vec3 color=vec3(0.5);" + type + " encoded=" + packed + ";";
				assertThrows(IllegalArgumentException.class, () -> fade(prefix
						+ "target=" + output + "(" + conversion + "(encoded));}\n"));
				assertThrows(IllegalArgumentException.class, () -> fade(prefix
						+ "float bits=" + conversion + "(encoded);target=" + output + "(bits);}\n"));
			}
		}
	}

	@Test
	void refusesEncodedFloatingOutputsReturnedByAHelper() {
		assertThrows(IllegalArgumentException.class, () -> fade("#version 460\n"
				+ "layout(location=0)out vec4 target;\n"
				+ "float encode(vec2 color){return uintBitsToFloat(packUnorm2x16(color));}\n"
				+ "void main(){target=vec4(encode(vec2(0.5)));}\n"));
	}

	@Test
	void preservesSupportedInlinePackingIntoFloatingOutputs() {
		String result = fade("#version 460\nlayout(location=0)out vec4 target;\n"
				+ "void main(){vec3 color=vec3(0.5);target.x=uintBitsToFloat(packUnorm2x16(color.rg));}\n");
		assertTrue(result.contains("vec3 ofCfiPacked0 = color;"));
		assertTrue(result.contains("FADE(ofCfiPacked0.rgb, sky.rgb)"));
		assertTrue(result.contains("uintBitsToFloat(packUnorm2x16(ofCfiPacked0.rg))"));
		assertFalse(result.contains("FADE(target"));
	}

	@Test
	void ignoresBitcastNamesInCommentsAndLongerIdentifiers() {
		String result = fade("#version 460\nlayout(location=0)out vec4 target;\n"
				+ "// uintBitsToFloat(encoded); intBitsToFloat(encoded);\n"
				+ "void main(){float uintBitsToFloatWeight=0.5;target=vec4(uintBitsToFloatWeight);}\n");
		assertTrue(result.contains("FADE(target.rgb, sky.rgb)"));
	}

}
