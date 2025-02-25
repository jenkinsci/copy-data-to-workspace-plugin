package hpi;

// Hudson/Jenkins imports
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import static hudson.Functions.isWindows;

// JUnit imports
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.BuildWatcher;

// Java standard imports
import java.io.IOException;

public class CopyDataToWorkspacePluginTest {
    private static final String TEST_FILE_NAME = "test.txt";
    private static final String TEST_CONTENT = "test content";
    private static final String TEST_DIR = "testDir";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public BuildWatcher watcher = new BuildWatcher();

    private FilePath userContent;
    private FilePath testDir;

    @Before
    public void setUp() throws IOException, InterruptedException {
        userContent = j.jenkins.getRootPath().child("userContent");
        testDir = userContent.child(TEST_DIR);
        testDir.mkdirs();
    }

    private void createTestFile() throws IOException, InterruptedException {
        testDir.child(TEST_FILE_NAME).write(TEST_CONTENT, "UTF-8");
    }

    private FreeStyleBuild createAndBuildProject(CopyDataToWorkspacePlugin plugin) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildWrappersList().add(plugin);
        return j.buildAndAssertSuccess(project);
    }

    @Test
    public void testBasicCopy() throws Exception {
        createTestFile();

        CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
            TEST_DIR,
            false,
            false
        );

        FreeStyleBuild build = createAndBuildProject(plugin);
        
        FilePath workspace = build.getWorkspace();
        assertTrue("File should be copied to workspace", 
            workspace.child(TEST_FILE_NAME).exists());
        assertEquals("File content should match", 
            TEST_CONTENT, workspace.child(TEST_FILE_NAME).readToString());
    }

    @Test
    public void testDeleteAfterBuild() throws Exception {
        createTestFile();

        CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
            TEST_DIR,
            false,
            true
        );

        FreeStyleBuild build = createAndBuildProject(plugin);
        
        FilePath workspace = build.getWorkspace();
        assertFalse("File should be deleted after build", 
            workspace.child(TEST_FILE_NAME).exists());
    }

    @Test
    public void testInvalidPaths() {
        // Test path with parent directory traversal
        FormValidation validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("../test");
        assertEquals("Parent directory traversal should be rejected", 
            FormValidation.Kind.ERROR, validation.kind);

        // Test path with tilde
        validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("~/test");
        assertEquals("Paths with tilde should be rejected", 
            FormValidation.Kind.ERROR, validation.kind);

        if (isWindows()) {
            // Test Windows absolute path
            validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("C:\\test");
            assertEquals("Windows absolute paths should be rejected", 
                FormValidation.Kind.ERROR, validation.kind);
    
            // Test UNC path
            validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("\\\\server\\share");
            assertEquals("UNC paths should be rejected", 
                FormValidation.Kind.ERROR, validation.kind);
        }
    }
}