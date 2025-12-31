import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.exists

public abstract class DownloadFrameworkTask
@Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val layout: ProjectLayout,
    private val fs: FileSystemOperations
)
    : DefaultTask() {

    @get:Input
    abstract val frameworkName: Property<String>

    @get:Input
    abstract val frameworkVersion: Property<String>

    @get:Input
    abstract val swiftpm: Property<Boolean>

    @get:Input
    abstract val projectRootDir: Property<String>

    private fun downloadFrom(url: String, path: String) =  ant.invokeMethod("get", mapOf("src" to url, "dest" to File(path)))

    @TaskAction
    fun download() {
        ensureBuildDirectory()

        val swiftPmSuffix = if (swiftpm.get()) "-swiftpm" else ""
        val zipUrl =
            "https://github.com/tigase/${frameworkName.get().lowercase()}${swiftPmSuffix}/releases/download/${frameworkVersion.get()}/${frameworkName.get()}.xcframework.zip"

        val frameworksBaseDir = Path(projectRootDir.get(), "build/frameworks/")
        val frameworkDirectory = Path(frameworksBaseDir.toString(), "${frameworkName.get()}.xcframework").toFile()
        val frameworkZipPath = Path(frameworksBaseDir.toString(), "${frameworkName.get()}.xcframework.zip").toFile()

        logger.lifecycle("Processing framework: `${frameworkName.get()}` version: `${frameworkVersion.get()}` to: `${frameworkDirectory}`; exists: ${frameworkDirectory.exists()}")

        if (!frameworkDirectory.exists()) {
            if (!frameworksBaseDir.exists()) {
                frameworksBaseDir.toFile().mkdir()
            }

            if (!frameworkZipPath.exists()) {
                logger.lifecycle("Downloading ${frameworkName.get()} framework from: `${zipUrl}` into ${frameworksBaseDir}")
                downloadFrom(zipUrl, frameworksBaseDir.toString())
            }

            if (!frameworkDirectory.exists()) {
                logger.lifecycle("Unzipping ${frameworkName.get()} framework into: ${frameworkDirectory}")

                fs.copy {
                    from(archiveOperations.zipTree("${frameworkZipPath}/"))
                    into("${frameworksBaseDir}")
                    filePermissions {
                        user {
                            read = true
                            execute = true
                        }
                    }
                }
            }
        }
    }

    private fun ensureBuildDirectory() {
        val buildDirectory = Path(projectRootDir.get(), "build/")
        if (!buildDirectory.exists()) {
            buildDirectory.toFile().mkdir()
        }
    }
}