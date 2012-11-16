package hpi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
