package fcw.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

import java.io.File;

public class CopySources extends DefaultTask {
    @Input public File destDir;
    @Input public ComponentIdentifier artifact;

    @TaskAction
    @SuppressWarnings({ "unchecked", "UnstableApiUsage" })
    public void act() {
        ArtifactResolutionResult result = getProject().getDependencies().createArtifactResolutionQuery()
            .forComponents(artifact)
            .withArtifacts(JvmLibrary.class, SourcesArtifact.class)
            .execute();

        File artifactFile = result.getResolvedComponents().stream()
            .flatMap(componentArtifactsResult -> componentArtifactsResult.getArtifacts(SourcesArtifact.class).stream())
            .map(artifactResult -> ((ResolvedArtifactResult) artifactResult).getFile())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No source artifact found for " + artifact));

        if (destDir.exists() && !destDir.delete()) {
            throw new IllegalStateException("Unable to delete/clear source folder " + destDir);
        }

        getProject().copy(spec -> {
            spec.from(getProject().zipTree(artifactFile));
            spec.into(destDir);
            spec.include("**/*.java");
            spec.setIncludeEmptyDirs(false);
        });
    }
}
