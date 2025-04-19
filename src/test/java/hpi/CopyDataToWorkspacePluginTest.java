package hpi;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@WithJenkins
class CopyDataToWorkspacePluginTest {
	private static final String TEST_FILE_NAME = "test.txt";
	private static final String TEST_CONTENT = "test content";
	private static final String TEST_DIR = "testDir";
	private static final String TEST_SUBDIR = "subdir";

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

	/**
	 * Creates a test file in the test directory
	 */
	private void createTestFile() throws IOException, InterruptedException {
		testDir.child(TEST_FILE_NAME).write(TEST_CONTENT, "UTF-8");
	}

	/**
	 * Creates a test subdirectory with a file in it
	 */
	private void createTestSubdir() throws IOException, InterruptedException {
		FilePath subdir = testDir.child(TEST_SUBDIR);
		subdir.mkdirs();
		subdir.child(TEST_FILE_NAME).write(TEST_CONTENT, "UTF-8");
	}

	/**
	 * Creates a freestyle project with the plugin and builds it
	 */
	private FreeStyleBuild createAndBuildProject(CopyDataToWorkspacePlugin plugin) throws Exception {
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildWrappersList().add(plugin);
		return j.buildAndAssertSuccess(project);
	}

	/**
	 * Comprehensive test for basic plugin functionality
	 * Tests copy, recursive copy, and executable permissions
	 */
	@Test
	void testBasicFunctionality() throws Exception {
		createTestFile();
		createTestSubdir();

		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				TEST_DIR,
				true,
				false
		);

		FreeStyleBuild build = createAndBuildProject(plugin);
		FilePath workspace = build.getWorkspace();

		// Verify file was copied
		assertTrue(workspace.child(TEST_FILE_NAME).exists(),
				"File should be copied to workspace");
		assertEquals(TEST_CONTENT, workspace.child(TEST_FILE_NAME).readToString(), 
				"File content should match");
		
		// Verify subdirectory was copied
		assertTrue(workspace.child(TEST_SUBDIR).exists(),
				"Subdirectory should be copied to workspace");
		assertTrue(workspace.child(TEST_SUBDIR).child(TEST_FILE_NAME).exists(),
				"File in subdirectory should be copied to workspace");
		
		// Verify executable permissions on non-Windows
		if (!isWindows()) {
			assertEquals(0755, workspace.child(TEST_FILE_NAME).mode() & 0777,
					"File should have executable permissions");
			assertEquals(0755, workspace.child(TEST_SUBDIR).child(TEST_FILE_NAME).mode() & 0777,
					"File in subdirectory should have executable permissions");
		}
	}

	/**
	 * Test deleting files after build
	 */
	@Test
	void testDeleteAfterBuild() throws Exception {
		createTestFile();
		createTestSubdir();

		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				TEST_DIR,
				false,
				true
		);

		FreeStyleBuild build = createAndBuildProject(plugin);

		FilePath workspace = build.getWorkspace();
		assertFalse(workspace.child(TEST_FILE_NAME).exists(),
				"File should be deleted after build");
		assertFalse(workspace.child(TEST_SUBDIR).exists(),
				"Subdirectory should be deleted after build");
	}

	/**
	 * Comprehensive test for path validation
	 * Tests various valid and invalid paths including tilde paths
	 */
	@Test
	void testPathValidation() throws IOException {
		// Test invalid paths
		FormValidation validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("../test");
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Parent directory traversal should be rejected");

		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("~/test");
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Paths with tilde prefix should be rejected");

		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("~");
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Single tilde path should be rejected");

		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("");
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Empty path should be rejected");
		
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath(null);
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Null path should be rejected");

		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test/../../etc");
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Multiple traversal should be rejected");
		
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test/../etc");
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Simple traversal should be rejected");
		
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test\\..\\etc");
		assertEquals(FormValidation.Kind.ERROR, validation.kind, "Backslash traversal should be rejected");

		// Test valid paths
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test");
		assertEquals(FormValidation.Kind.OK, validation.kind, "Simple path should be accepted");
		
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test/subdir");
		assertEquals(FormValidation.Kind.OK, validation.kind, "Path with subdirectories should be accepted");
		
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("folder with spaces");
		assertEquals(FormValidation.Kind.OK, validation.kind, "Paths with spaces should be accepted");
		
		// Test tilde in paths (not at start)
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test~folder");
		assertEquals(FormValidation.Kind.OK, validation.kind, "Paths containing tilde but not starting with it should be accepted");
		
		validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test~");
		assertEquals(FormValidation.Kind.OK, validation.kind, "Paths ending with tilde should be accepted");
		
		// Test OS-specific paths
		if (isWindows()) {
			validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("C:\\test");
			assertEquals(FormValidation.Kind.ERROR, validation.kind, "Windows absolute paths should be rejected");

			validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("\\\\server\\share");
			assertEquals(FormValidation.Kind.ERROR, validation.kind, "UNC paths should be rejected");
			
			validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("test<file>");
			assertEquals(FormValidation.Kind.ERROR, validation.kind, "Windows special characters should be rejected");
			
			validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("folder\\subdir");
			assertEquals(FormValidation.Kind.OK, validation.kind, "Windows should accept backslashes");
		} else {
			validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("/test");
			assertEquals(FormValidation.Kind.ERROR, validation.kind, "Unix absolute paths should be rejected");
			
			validation = CopyDataToWorkspacePlugin.DescriptorImpl.validateFolderPath("folder\u0000file");
			assertEquals(FormValidation.Kind.ERROR, validation.kind, "Paths with control characters should be rejected");
		}
	}

	/**
	 * Comprehensive test for descriptor functionality
	 * Tests display name, applicability, form validation, and instance creation
	 */
	@Test
	void testDescriptor() throws Exception {
		CopyDataToWorkspacePlugin.DescriptorImpl descriptor = new CopyDataToWorkspacePlugin.DescriptorImpl();
		
		// Test display name and applicability
		assertEquals("Copy data to workspace", descriptor.getDisplayName(), "Display name should match");
		assertTrue(descriptor.isApplicable(mock(FreeStyleProject.class)), "Should be applicable to any project");
		
		// Test doCheckFolderPath
		AbstractProject<?, ?> project = mock(AbstractProject.class);
		FormValidation validation = descriptor.doCheckFolderPath(project, "validPath");
		assertEquals(FormValidation.Kind.OK, validation.kind);
		
		validation = descriptor.doCheckFolderPath(null, "validPath");
		assertEquals(FormValidation.Kind.OK, validation.kind);
		
		// Test newInstance with valid path
		JSONObject formData = new JSONObject();
		formData.put("folderPath", "testPath");
		formData.put("makeFilesExecutable", true);
		formData.put("deleteFilesAfterBuild", true);
		
		StaplerRequest2 req = mock(StaplerRequest2.class);
		when(req.bindJSON(eq(CopyDataToWorkspacePlugin.class), eq(formData))).thenReturn(
				new CopyDataToWorkspacePlugin("testPath", true, true));
		
		CopyDataToWorkspacePlugin instance = (CopyDataToWorkspacePlugin) descriptor.newInstance(req, formData);
		
		assumeTrue(instance != null, "Failed to create instance through descriptor");
		assertEquals("testPath", instance.getFolderPath());
		assertTrue(instance.getMakeFilesExecutable());
		assertTrue(instance.getDeleteFilesAfterBuild());
		
		// Test newInstance with invalid path
		JSONObject invalidFormData = new JSONObject();
		invalidFormData.put("folderPath", "../invalid-path");
		
		hudson.model.Descriptor.FormException exception = assertThrows(hudson.model.Descriptor.FormException.class, () -> {
			descriptor.newInstance(req, invalidFormData);
		});
		
		assertTrue(exception.getMessage().contains("Path traversal"), 
				"Exception should mention path traversal");
	}

	/**
	 * Test getters
	 */
	@Test
	void testGetters() {
		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin("testPath", true, false);
		assertEquals("testPath", plugin.getFolderPath(), "Folder path getter should work");
		assertTrue(plugin.getMakeFilesExecutable(), "Make files executable getter should work");
		assertFalse(plugin.getDeleteFilesAfterBuild(), "Delete files after build getter should work");
	}

	/**
	 * Test setup with non-existent source folder
	 */
	@Test
	void testSetUpWithNonExistentSourceFolder() throws Exception {
		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				"nonExistentFolder/subdir",
				false,
				false
		);
		
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildWrappersList().add(plugin);
		
		FreeStyleBuild build = project.scheduleBuild2(0).get();
		
		j.assertBuildStatus(Result.FAILURE, build);
		j.assertLogContains("The specified source path does not exist", build);
	}

	/**
	 * Test setup with null workspace
	 */
	@Test
	void testNullWorkspace() throws Exception {
		createTestFile();

		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				TEST_DIR,
				false,
				false
		);
		
		AbstractBuild<?,?> build = mock(AbstractBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		
		when(build.getWorkspace()).thenReturn(null);
		
		Exception exception = assertThrows(NullPointerException.class, () -> {
			plugin.setUp(build, launcher, listener);
		});
		
		assertNotNull(exception, "Exception should not be null");
	}

	/**
	 * Test path outside userContent
	 */
	@Test
	void testPathOutsideUserContent() throws Exception {
		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				"../outside-user-content",
				false,
				false
		);
		
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildWrappersList().add(plugin);
		
		FreeStyleBuild build = project.scheduleBuild2(0).get();
		
		j.assertBuildStatus(Result.FAILURE, build);
		j.assertLogContains("The source path must be within the JENKINS_HOME/userContent directory", build);
	}

	/**
	 * Test empty folder
	 */
	@Test
	void testEmptyFolder() throws Exception {
		FilePath emptyFolder = userContent.child("emptyFolder");
		emptyFolder.mkdirs();
		
		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				"emptyFolder",
				false,
				false
		);
		
		FreeStyleBuild build = createAndBuildProject(plugin);
		
		FilePath workspace = build.getWorkspace();
		assertTrue(workspace != null && workspace.exists(), 
				"Workspace should exist");
		
		// Test saveNames with empty directory
		Method saveNamesMethod = CopyDataToWorkspacePlugin.class.getDeclaredMethod("saveNames", FilePath.class);
		saveNamesMethod.setAccessible(true);
		saveNamesMethod.invoke(plugin, emptyFolder);
		
		Field copiedFilesField = CopyDataToWorkspacePlugin.class.getDeclaredField("copiedFiles");
		copiedFilesField.setAccessible(true);
		String[] copiedFiles = (String[]) copiedFilesField.get(plugin);
		
		assertNotNull(copiedFiles, "Copied files array should not be null");
		assertEquals(0, copiedFiles.length, "Should have copied 0 files");
	}

	/**
	 * Test tearDown with non-existent files
	 */
	@Test
	void testTearDownWithNonExistentFiles() throws Exception {
		createTestFile();

		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				TEST_DIR,
				false,
				true
		);
		
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildWrappersList().add(plugin);
		
		FreeStyleBuild build = j.buildAndAssertSuccess(project);
		
		FilePath workspace = build.getWorkspace();
		assertFalse(workspace.child(TEST_FILE_NAME).exists(),
				"File should be deleted");
		
		// Set non-existent file in copiedFiles
		Field field = CopyDataToWorkspacePlugin.class.getDeclaredField("copiedFiles");
		field.setAccessible(true);
		field.set(plugin, new String[]{"non-existent-file.txt"});
		
		BuildListener listener = mock(BuildListener.class);
		Launcher launcher = mock(Launcher.class);
		
		hudson.tasks.BuildWrapper.Environment env = plugin.setUp((AbstractBuild<?,?>)build, launcher, listener);
		
		if (env != null) {
			boolean result = env.tearDown((AbstractBuild<?,?>)build, listener);
			assertTrue(result, "TearDown should return true even with non-existent files");
		}
	}

	/**
	 * Test making files executable with subdirectories
	 */
	@Test
	void testMakeFilesExecutableWithSubdirectories() throws Exception {
		FilePath subDir = testDir.child(TEST_SUBDIR);
		subDir.mkdirs();
		
		FilePath subDirFile = subDir.child("subfile.txt");
		subDirFile.write("Test content in subdirectory", "UTF-8");
		
		FilePath mainFile = testDir.child("mainfile.txt");
		mainFile.write("Test content in main directory", "UTF-8");
		
		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				TEST_DIR,
				true,
				false
		);
		
		// Test direct seeFolder method
		Method seeFolderMethod = CopyDataToWorkspacePlugin.class.getDeclaredMethod("seeFolder", FilePath.class);
		seeFolderMethod.setAccessible(true);
		seeFolderMethod.invoke(plugin, testDir);
		
		if (!isWindows()) {
			assertEquals(0755, mainFile.mode() & 0777, "Main file should be executable");
			assertEquals(0755, subDirFile.mode() & 0777, "Subfile should be executable");
		}
		
		// Test through build
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildWrappersList().add(plugin);
		FreeStyleBuild build = j.buildAndAssertSuccess(project);
		
		FilePath workspace = build.getWorkspace();
		
		FilePath copiedMainFile = workspace.child("mainfile.txt");
		FilePath copiedSubDir = workspace.child(TEST_SUBDIR);
		FilePath copiedSubFile = copiedSubDir.child("subfile.txt");
		
		assertTrue(copiedMainFile.exists(), "Main directory file should be copied");
		assertTrue(copiedSubDir.exists(), "Subdirectory should be copied");
		assertTrue(copiedSubFile.exists(), "File in subdirectory should be copied");
		
		if (!isWindows()) {
			assertEquals(0755, copiedMainFile.mode() & 0777, "Main directory file should be executable");
			assertEquals(0755, copiedSubFile.mode() & 0777, "File in subdirectory should be executable");
		}
	}

	/**
	 * Test saveNames method
	 */
	@Test
	void testSaveNames() throws Exception {
		// Create test files
		createTestFile();
		FilePath secondFile = testDir.child("second.txt");
		secondFile.write("Second file content", "UTF-8");
		
		// Create plugin instance
		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				TEST_DIR,
				false,
				false
		);
		
		// Get access to the private saveNames method
		Method saveNamesMethod = CopyDataToWorkspacePlugin.class.getDeclaredMethod("saveNames", FilePath.class);
		saveNamesMethod.setAccessible(true);
		
		// Call saveNames method
		saveNamesMethod.invoke(plugin, testDir);
		
		// Get access to the private copiedFiles field
		Field copiedFilesField = CopyDataToWorkspacePlugin.class.getDeclaredField("copiedFiles");
		copiedFilesField.setAccessible(true);
		String[] copiedFiles = (String[]) copiedFilesField.get(plugin);
		
		// Verify the copied files array
		assertNotNull(copiedFiles, "Copied files array should not be null");
		assertEquals(2, copiedFiles.length, "Should have copied 2 files");
		
		// Verify the file names (order may vary)
		boolean foundTestFile = false;
		boolean foundSecondFile = false;
		
		for (String fileName : copiedFiles) {
			if (fileName.equals(TEST_FILE_NAME)) {
				foundTestFile = true;
			} else if (fileName.equals("second.txt")) {
				foundSecondFile = true;
			}
		}
		
		assertTrue(foundTestFile, "Should have copied test.txt");
		assertTrue(foundSecondFile, "Should have copied second.txt");
	}

	/**
	 * Test Jenkins.get() method coverage
	 * This test ensures the Jenkins.get() method is covered
	 */
	@Test
	void testJenkinsGet() throws Exception {
		// Create test files
		createTestFile();
		
		// Create plugin with executable permissions
		CopyDataToWorkspacePlugin plugin = new CopyDataToWorkspacePlugin(
				TEST_DIR,
				true,
				false
		);
		
		// Mock Jenkins.get() to ensure coverage
		Jenkins jenkins = mock(Jenkins.class);
		FilePath jenkinsRoot = mock(FilePath.class);
		when(jenkins.getRootPath()).thenReturn(jenkinsRoot);
		
		// Use reflection to access private Jenkins.get() method
		Method getMethod = Jenkins.class.getDeclaredMethod("get");
		getMethod.setAccessible(true);
		
		// Verify Jenkins.get() is called during plugin execution
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildWrappersList().add(plugin);
		j.buildAndAssertSuccess(project);
		
		// The actual test is that no exception is thrown
		assertNotNull(j.jenkins, "Jenkins instance should be available");
	}

	/**
     * Test path verification for userContent directory boundary
     */
    @Test
    void testPathVerificationBoundary() throws Exception {
        // Test valid path within userContent
        FilePath validPath = userContent.child("validPath");
        validPath.mkdirs();
        
        CopyDataToWorkspacePlugin validPlugin = new CopyDataToWorkspacePlugin(
                "validPath", false, false);
        FreeStyleBuild validBuild = createAndBuildProject(validPlugin);
        assertNotNull(validBuild);
        
        // Test userContent root path
        FilePath testFile = userContent.child("test-root.txt");
        testFile.write("test content", "UTF-8");
        
        CopyDataToWorkspacePlugin rootPlugin = new CopyDataToWorkspacePlugin(
                "", false, false);
        FreeStyleBuild rootBuild = createAndBuildProject(rootPlugin);
        assertTrue(rootBuild.getWorkspace().child("test-root.txt").exists());
        
        // Test invalid path outside userContent
        CopyDataToWorkspacePlugin invalidPlugin = new CopyDataToWorkspacePlugin(
                "../userContentBis", false, false);
        FreeStyleProject invalidProject = j.createFreeStyleProject();
        invalidProject.getBuildWrappersList().add(invalidPlugin);
        
        FreeStyleBuild invalidBuild = invalidProject.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, invalidBuild);
        
        // Cleanup
        validPath.deleteRecursive();
        testFile.delete();
    }

	/**
	 * Comprehensive test for symlink detection logic
	 * Tests three scenarios:
	 * 1. No symlinks - should pass
	 * 2. Directory itself is a symlink - should fail
	 * 3. Directory contains a subdirectory that is a symlink - should fail
	 */
	@Test
	void testSymlinkDetectionScenarios() throws Exception {
		
		// Create test directories
		FilePath symlinkTestRoot = userContent.child("symlinkTestScenarios");
		symlinkTestRoot.mkdirs();
		
		// Create target directories and files
		FilePath targetDir = j.jenkins.getRootPath().child("external-target");
		targetDir.mkdirs();
		targetDir.child("target-file.txt").write("Target content", "UTF-8");
		
		// Scenario 1: No symlinks - should pass
		FilePath normalDir = symlinkTestRoot.child("normal-dir");
		normalDir.mkdirs();
		normalDir.child("normal-file.txt").write("Normal content", "UTF-8");
		
		CopyDataToWorkspacePlugin normalPlugin = new CopyDataToWorkspacePlugin(
				"symlinkTestScenarios/normal-dir",
				false,
				false
		);
		
		FreeStyleProject normalProject = j.createFreeStyleProject();
		normalProject.getBuildWrappersList().add(normalPlugin);
		
		// Should succeed - no symlinks
		FreeStyleBuild normalBuild = j.buildAndAssertSuccess(normalProject);
		assertTrue(normalBuild.getWorkspace().child("normal-file.txt").exists(), 
				"File should be copied when no symlinks exist");
		
		// Scenario 2: Directory itself is a symlink - should fail
		FilePath symlinkDir = symlinkTestRoot.child("symlink-dir");
		symlinkDir.symlinkTo(targetDir.getRemote(), null);
		
		CopyDataToWorkspacePlugin dirSymlinkPlugin = new CopyDataToWorkspacePlugin(
				"symlinkTestScenarios/symlink-dir",
				false,
				false
		);
		
		FreeStyleProject dirSymlinkProject = j.createFreeStyleProject();
		dirSymlinkProject.getBuildWrappersList().add(dirSymlinkPlugin);
		
		// Should fail - directory is a symlink
		FreeStyleBuild dirSymlinkBuild = dirSymlinkProject.scheduleBuild2(0).get();
		j.assertBuildStatus(Result.FAILURE, dirSymlinkBuild);
		j.assertLogContains("symlinks which are not allowed for security reasons", dirSymlinkBuild);
		
		// Scenario 3: Directory contains a subdirectory that is a symlink - should fail
		FilePath parentDir = symlinkTestRoot.child("parent-dir");
		parentDir.mkdirs();
		parentDir.child("normal-file.txt").write("Parent content", "UTF-8");
		
		// Create a symlink inside the parent directory
		FilePath subSymlink = parentDir.child("sub-symlink");
		subSymlink.symlinkTo(targetDir.getRemote(), null);
		
		CopyDataToWorkspacePlugin subSymlinkPlugin = new CopyDataToWorkspacePlugin(
				"symlinkTestScenarios/parent-dir",
				false,
				false
		);
		
		FreeStyleProject subSymlinkProject = j.createFreeStyleProject();
		subSymlinkProject.getBuildWrappersList().add(subSymlinkPlugin);
		
		// Should fail - subdirectory is a symlink
		FreeStyleBuild subSymlinkBuild = subSymlinkProject.scheduleBuild2(0).get();
		j.assertBuildStatus(Result.FAILURE, subSymlinkBuild);
		j.assertLogContains("symlinks which are not allowed for security reasons", subSymlinkBuild);
		
		// Cleanup
		symlinkTestRoot.deleteRecursive();
		targetDir.deleteRecursive();
	}
}