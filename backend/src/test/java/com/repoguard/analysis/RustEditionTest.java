package com.repoguard.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class RustEditionTest {
    @TempDir Path root;
    @Test void resolvesWorkspaceEditionWithoutExecutingBuildOrProjectConfiguration() throws Exception {
        Files.writeString(root.resolve("Cargo.toml"),"[workspace.package]\nedition = \"2024\"\n");
        Path crate=Files.createDirectories(root.resolve("crate/src"));
        Files.writeString(root.resolve("crate/Cargo.toml"),"[package]\nname = \"passive\"\nedition.workspace = true\nbuild = \"must-never-run.rs\"\n");
        assertThat(RustEdition.of(crate.resolve("lib.rs"),root)).isEqualTo("2024");
    }
    @Test void honorsPackageEditionAndCargoDefaultButRejectsAmbiguousValues() throws Exception {
        Path manifest=root.resolve("Cargo.toml"),file=root.resolve("lib.rs");
        Files.writeString(manifest,"[package]\nedition = '2018'\n");assertThat(RustEdition.of(file,root)).isEqualTo("2018");
        Files.writeString(manifest,"[package]\nname = 'passive'\n");assertThat(RustEdition.of(file,root)).isEqualTo("2015");
        Files.writeString(manifest,"[package]\nedition = '2099'\n");assertThatThrownBy(()->RustEdition.of(file,root)).isInstanceOf(AnalysisException.class);
    }
}
