/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.tests.plugin.sftp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.syncany.plugins.Plugin;
import org.syncany.plugins.Plugins;
import org.syncany.plugins.sftp.SftpTransferManager;
import org.syncany.plugins.sftp.SftpTransferPlugin;
import org.syncany.plugins.sftp.SftpTransferSettings;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferManager;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferPluginOption;
import org.syncany.plugins.transfer.TransferPluginOption.ValidationResult;
import org.syncany.plugins.transfer.TransferPluginOptions;
import org.syncany.plugins.transfer.files.RemoteFile;
import org.syncany.plugins.transfer.files.MultichunkRemoteFile;
import org.syncany.tests.util.TestFileUtil;

public class SftpConnectionPluginTest {
	private static File tempLocalSourceDir;
	
	private SftpTransferSettings validSftpTransferSettings;
	private SftpTransferSettings invalidSftpTransferSettings;

	@BeforeClass
	public static void beforeTestSetup() {
		try {
			EmbeddedSftpServerTest.startServer();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@AfterClass
	public static void tearDown() {
		try {
			EmbeddedSftpServerTest.stopServer();
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Before
	public void setUp() throws Exception {
		File rootDir = TestFileUtil.createTempDirectoryInSystemTemp();

		tempLocalSourceDir = new File(rootDir+"/local");
		tempLocalSourceDir.mkdir();
		
		TransferPlugin pluginInfo = Plugins.get("sftp", TransferPlugin.class);
		validSftpTransferSettings = pluginInfo.createEmptySettings();
		validSftpTransferSettings.setHostname(EmbeddedSftpServerTest.HOST);
		validSftpTransferSettings.setUsername("user");
		validSftpTransferSettings.setPassword("pass");
		validSftpTransferSettings.setPort(EmbeddedSftpServerTest.PORT);
		validSftpTransferSettings.setPath("/repo");		
		validSftpTransferSettings.setCheckHostKey(SftpTransferSettings.CheckHostKeyMode.NO);

		invalidSftpTransferSettings = pluginInfo.createEmptySettings();
	}
	
	@After
	public void tear(){
		TestFileUtil.deleteDirectory(tempLocalSourceDir);
	}
	
	@Test
	public void testLoadPluginAndCreateTransferManager() throws StorageException {
		loadPluginAndCreateTransferManager();
	}
	
	@Test
	public void testLocalPluginInfo() {
		Plugin pluginInfo = Plugins.get("sftp");
		
		assertNotNull("PluginInfo should not be null.", pluginInfo);
		assertEquals("Plugin ID should be 'sftp'.", "sftp", pluginInfo.getId());
		assertNotNull("Plugin version should not be null.", pluginInfo.getVersion());
		assertNotNull("Plugin name should not be null.", pluginInfo.getName());
	}
	
	@Test(expected=StorageException.class)
	public void testConnectToNonExistantFolder() throws StorageException {
		TransferPlugin pluginInfo = Plugins.get("sftp", TransferPlugin.class);
				
		SftpTransferSettings connection = pluginInfo.createEmptySettings();
		connection.setHostname(EmbeddedSftpServerTest.HOST);
		connection.setUsername("user");
		connection.setPassword("pass");
		connection.setPort(EmbeddedSftpServerTest.PORT);
		connection.setPath("/path/does/not/exist");
		validSftpTransferSettings.setCheckHostKey(SftpTransferSettings.CheckHostKeyMode.NO);
		
		TransferManager transferManager = pluginInfo.createTransferManager(connection, null);
		
		// This should cause a Storage exception, because the path does not exist
		transferManager.connect();	
		transferManager.init(true);
	}

	@Test
	public void testTransferSettingsFields() throws StorageException {
		// Checks the fields types and annotations to catch runtime errors

		SftpTransferSettings settings = validSftpTransferSettings;

		List<TransferPluginOption> pluginOptions =
				TransferPluginOptions.getOrderedOptions(settings.getClass());

		for (TransferPluginOption option : pluginOptions) {
			String fieldName = option.getName();
			String fieldValue = settings.getField(fieldName);
			ValidationResult validationRes = option.isValid(fieldValue);
			assertEquals("Value '" + fieldValue +  "' of field '" + fieldName +
					"' must pass validity check",
					ValidationResult.VALID, validationRes);
		}
	}

	@Test(expected=StorageException.class)
	public void testInvalidSettingsValidation() throws StorageException {
		assertFalse(invalidSftpTransferSettings.isValid());
		invalidSftpTransferSettings.validateRequiredFields();
	}

	@Test
	public void testValidSettingsValidation() throws StorageException {
		assertTrue(validSftpTransferSettings.isValid());
		validSftpTransferSettings.validateRequiredFields();
	}
	
	@Test(expected=StorageException.class)
	public void testConnectWithInvalidSettings() throws StorageException {
		TransferPlugin pluginInfo = Plugins.get("sftp", TransferPlugin.class);
		
		TransferManager transferManager =
			pluginInfo.createTransferManager(invalidSftpTransferSettings, null);
		
		// This should cause a Storage exception, because the path does not exist
		transferManager.connect();		
	}

	@Test
	public void testSftpUploadListDownloadAndDelete() throws Exception {				
		// Generate test files
		Map<String, File> inputFiles = generateTestInputFile();

		// Create connection, upload, list, download
		TransferManager transferManager = loadPluginAndCreateTransferManager();		
		transferManager.connect();	
		transferManager.init(true);

		Map<File, RemoteFile> uploadedFiles = uploadChunkFiles(transferManager, inputFiles.values());
		Map<String, RemoteFile> remoteFiles = transferManager.list(RemoteFile.class);
		Map<RemoteFile, File> downloadedLocalFiles = downloadRemoteFiles(transferManager, remoteFiles.values());

		// Compare
		assertEquals("Number of uploaded files should be the same as the input files.", uploadedFiles.size(), remoteFiles.size());
		assertEquals("Number of remote files should be the same as the downloaded files.", remoteFiles.size(), downloadedLocalFiles.size());
		
		for (Map.Entry<String, File> inputFileEntry : inputFiles.entrySet()) {		
			File inputFile = inputFileEntry.getValue();
			
			RemoteFile uploadedFile = uploadedFiles.get(inputFile);
			File downloadedLocalFile = downloadedLocalFiles.get(uploadedFile);
			
			assertNotNull("Cannot be null.", uploadedFile);
			assertNotNull("Cannot be null.", downloadedLocalFile);
			
			byte[] checksumOriginalFile = TestFileUtil.createChecksum(inputFile);
			byte[] checksumDownloadedFile = TestFileUtil.createChecksum(downloadedLocalFile);
			
			assertArrayEquals("Uploaded file differs from original file.", checksumOriginalFile, checksumDownloadedFile);			
		}
		
		// Delete
		for (RemoteFile remoteFileToDelete : uploadedFiles.values()) {			
			transferManager.delete(remoteFileToDelete);
		}
		
		Map<String, RemoteFile> remoteFiles2 = transferManager.list(RemoteFile.class);
		Map<RemoteFile, File> downloadedLocalFiles2 = downloadRemoteFiles(transferManager, remoteFiles2.values());
		
		for (RemoteFile remoteFileToBeDeleted : downloadedLocalFiles2.keySet()) {			
			assertFalse("Could not delete remote file.",downloadedLocalFiles2.containsKey(remoteFileToBeDeleted));
		}
	}	
	
	private Map<String, File> generateTestInputFile() throws IOException {
		Map<String, File> inputFilesMap = new HashMap<String, File>();
		List<File> inputFiles = TestFileUtil.createRandomFilesInDirectory(tempLocalSourceDir, 50*1024, 10);
		
		for (File file : inputFiles) {
			inputFilesMap.put(file.getName(), file);
		}
		
		return inputFilesMap;
	}
	
	private Map<File, RemoteFile> uploadChunkFiles(TransferManager transferManager, Collection<File> inputFiles) throws StorageException {
		Map<File, RemoteFile> inputFileOutputFile = new HashMap<File, RemoteFile>();
		
		throw new RuntimeException("Not yet implemented");
		/*
		for (File inputFile : inputFiles) {
			RemoteFile remoteOutputFile = new RemoteFile(inputFile.getName());
			transferManager.upload(inputFile, remoteOutputFile);
			
			inputFileOutputFile.put(inputFile, remoteOutputFile);			
		}
		
		return inputFileOutputFile;*/
	}
	
	private Map<RemoteFile, File> downloadRemoteFiles(TransferManager transferManager, Collection<RemoteFile> remoteFiles) throws StorageException {
		Map<RemoteFile, File> downloadedLocalFiles = new HashMap<RemoteFile, File>();
		
		for (RemoteFile remoteFile : remoteFiles) {
			File downloadedLocalFile = new File(tempLocalSourceDir+"/downloaded-"+remoteFile.getName());
			transferManager.download(remoteFile, downloadedLocalFile);
			
			downloadedLocalFiles.put(remoteFile, downloadedLocalFile);
			
			assertTrue("Downloaded file does not exist.", downloadedLocalFile.exists());
		}
		
		return downloadedLocalFiles;
	}	
	
	private TransferManager loadPluginAndCreateTransferManager() throws StorageException {
		TransferPlugin pluginInfo = Plugins.get("sftp", TransferPlugin.class);	
		
		TransferManager transferManager = pluginInfo.createTransferManager(validSftpTransferSettings, null);

		assertEquals("SftpPlugin expected.", SftpTransferPlugin.class, pluginInfo.getClass());
		assertEquals("SftpConnection expected.", SftpTransferSettings.class, validSftpTransferSettings.getClass());
		assertEquals("SftpTransferManager expected.", SftpTransferManager.class, transferManager.getClass());
		
		return transferManager;
	}
}
