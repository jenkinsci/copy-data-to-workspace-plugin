package hpi;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;

import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class CopyDataToWorkspacePluginTest {
  private static final String TEST_FILE_NAME = "test.txt";
  private static final String TEST_CONTENT = "test content";
  private static final String TEST_DIR = "testDir";

  private JenkinsRule j;

  private FilePath userContent;
  private FilePath testDir;

  @BeforeEach
  void setUp(JenkinsRule rule) throws IOException, InterruptedException {
    j = rule;
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
  void testBasicCopy() throws Exception {
    createTestFile();

    CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
            TEST_DIR,
            false,
            false
    );

    FreeStyleBuild build = createAndBuildProject(plugin);

    FilePath workspace = build.getWorkspace();
    assertTrue(workspace.child(TEST_FILE_NAME).exists(),
            "File should be copied to workspace");
    assertEquals(TEST_CONTENT, workspace.child(TEST_FILE_NAME).readToString(), "File content should match");
  }

  @Test
  void testDeleteAfterBuild() throws Exception {
    createTestFile();

    CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
            TEST_DIR,
            false,
            true
    );

    FreeStyleBuild build = createAndBuildProject(plugin);

    FilePath workspace = build.getWorkspace();
    assertFalse(workspace.child(TEST_FILE_NAME).exists(),
            "File should be deleted after build");
  }

  @Test
  void testInvalidPaths() {
    // Test path with parent directory traversal
    FormValidation validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("../test");
    assertEquals(FormValidation.Kind.ERROR, validation.kind, "Parent directory traversal should be rejected");

    // Test path with tilde
    validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("~/test");
    assertEquals(FormValidation.Kind.ERROR, validation.kind, "Paths with tilde should be rejected");

    if (isWindows()) {
      // Test Windows absolute path
      validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("C:\\test");
      assertEquals(FormValidation.Kind.ERROR, validation.kind, "Windows absolute paths should be rejected");

      // Test UNC path
      validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("\\\\server\\share");
      assertEquals(FormValidation.Kind.ERROR, validation.kind, "UNC paths should be rejected");
    }
  }
}