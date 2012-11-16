package hpi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import hudson.FilePath;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Y. Schumann <yves@eisfair.org>
 */
public class CopyDataToWorkspacePluginTest {

    private final String folderPath1 = "foo";
    private final String folderPath2 = " asdf ";
    private final boolean makeFilesExecutable = true;
    private final boolean deleteFilesAfterBuild = true;
    private CopyDataToWorkspacePlugin copyDataToWorkspacePlugin1;
    private CopyDataToWorkspacePlugin copyDataToWorkspacePlugin2;

    @Before
    public void setUp() throws Exception {
        copyDataToWorkspacePlugin1 = new CopyDataToWorkspacePlugin(folderPath1, makeFilesExecutable, deleteFilesAfterBuild);
        copyDataToWorkspacePlugin2 = new CopyDataToWorkspacePlugin(folderPath2, !makeFilesExecutable, !deleteFilesAfterBuild);
    }

    @Test
    public void testGetFolderPath() {
        assertEquals("Returned folder path not matching given one", folderPath1, copyDataToWorkspacePlugin1.getFolderPath());
        assertEquals("Returned folder path not matching given one", folderPath2, copyDataToWorkspacePlugin2.getFolderPath());
    }

    @Test
    public void testGetMakeFilesExecutable() {
        assertTrue("getMakeFilesExecutable() must return true", copyDataToWorkspacePlugin1.getMakeFilesExecutable());
        assertFalse("getMakeFilesExecutable() must return false", copyDataToWorkspacePlugin2.getMakeFilesExecutable());
    }

    @Test
    public void testGetDeleteFilesAfterBuild() {
        assertTrue("getDeleteFilesAfterBuild() must return true", copyDataToWorkspacePlugin1.getDeleteFilesAfterBuild());
        assertFalse("getDeleteFilesAfterBuild() must return false", copyDataToWorkspacePlugin2.getDeleteFilesAfterBuild());
    }

    @Test
    public void testSaveNames() {
        final String unitTestfile1 = "unitTestFile1_";
        final String unitTestfile2 = "unitTestFile2_";
        final String unitTestfile3 = "unitTestFile3_";
        File tempFile1 = null;
        File tempFile2 = null;
        File tempFile3 = null;
        try {
            tempFile1 = File.createTempFile(unitTestfile1, null);
            tempFile2 = File.createTempFile(unitTestfile2, null);
            tempFile3 = File.createTempFile(unitTestfile3, null);
            copyDataToWorkspacePlugin1.saveNames(new FilePath(tempFile1.getParentFile()));
            copyDataToWorkspacePlugin1.saveNames(new FilePath(tempFile2.getParentFile()));
            copyDataToWorkspacePlugin1.saveNames(new FilePath(tempFile3.getParentFile()));
            assertTrue("Internal list of copied filenames must contain " + unitTestfile1, copyDataToWorkspacePlugin1.getCopiedFilenames()
                    .contains(tempFile1.getName()));
            assertTrue("Internal list of copied filenames must contain " + unitTestfile2, copyDataToWorkspacePlugin1.getCopiedFilenames()
                    .contains(tempFile2.getName()));
            assertTrue("Internal list of copied filenames must contain " + unitTestfile3, copyDataToWorkspacePlugin1.getCopiedFilenames()
                    .contains(tempFile3.getName()));
        } catch (final IOException e) {
            fail("Error during unitest, handling of temporary files failed: " + e.getMessage());
        } catch (final InterruptedException e) {
            fail("Error during unitest, handling of temporary files failed: " + e.getMessage());
        } finally {
            if (tempFile1 != null && tempFile1.exists()) {
                tempFile1.delete();
            }
            if (tempFile2 != null && tempFile2.exists()) {
                tempFile2.delete();
            }
            if (tempFile3 != null && tempFile3.exists()) {
                tempFile3.delete();
            }
        }
    }

    @Test
    public void testTrimLeftAndRight() {
        // String with no spaces
        String trimmedFolderPath = CopyDataToWorkspacePlugin.trimLeftAndRight(folderPath1);
        assertFalse("Trimmed string must not contain any spaces", trimmedFolderPath.contains(" "));
        assertEquals("Wrong length of returned string", folderPath1.length(), trimmedFolderPath.length());

        // String with one leading and one trailing space
        trimmedFolderPath = CopyDataToWorkspacePlugin.trimLeftAndRight(folderPath2);
        assertFalse("Trimmed string must not contain any spaces", trimmedFolderPath.contains(" "));
        assertEquals("Wrong length of returned string", folderPath2.length() - 2, trimmedFolderPath.length());

        // String with multiple leading and trailing spaces
        final String multipleSpaces = "   asdf   ";
        trimmedFolderPath = CopyDataToWorkspacePlugin.trimLeftAndRight(multipleSpaces);
        assertFalse("Trimmed string must not contain any spaces", trimmedFolderPath.contains(" "));
        assertEquals("Wrong length of returned string", multipleSpaces.length() - 6, trimmedFolderPath.length());
    }
}
