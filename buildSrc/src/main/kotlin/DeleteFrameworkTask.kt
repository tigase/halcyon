import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.Path

public abstract class DeleteFrameworkTask
@Inject constructor(
    private val fs: FileSystemOperations,
) : Delete() {
    @get:Input
    abstract val frameworkName: Property<String>

    private val frameworksBaseDir = Path(project.rootDir.path, "build/frameworks/")

    @get:Destroys
    abstract val frameworkDirectoryPath: Property<Path>

    @get:Destroys
    abstract val frameworkZipPath: Property<Path>

    @TaskAction
    fun deleteAction() {
        val directoryToDelete = frameworkDirectoryPath.get().toFile()
        val zipToDelete = frameworkZipPath.get().toFile()

        if (directoryToDelete.exists()) {
            logger.lifecycle("Deleting ${frameworkName.get()} directory from: ${directoryToDelete}")

            fs.delete {
                if (directoryToDelete.exists()) delete(directoryToDelete)
            }
        }

        if (zipToDelete.exists()) {
            logger.lifecycle("Deleting ${frameworkName.get()}.zip from: ${zipToDelete}")

            fs.delete {
                if (zipToDelete.exists()) delete(zipToDelete)
            }
        }
    }

    fun getFrameworkZipPath(frameworkName: String): Path {
        return Path(frameworksBaseDir.toString(), "${frameworkName}.xcframework.zip")
    }

    fun getFrameworkDirectoryPath(frameworkName: String): Path {
        return Path(frameworksBaseDir.toString(), "${frameworkName}.xcframework")
    }
}
