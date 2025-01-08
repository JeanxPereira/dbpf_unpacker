/****************************************************************************
* Copyright (C) 2019 Eric Mor
*
* This file is part of SporeModder FX.
*
* SporeModder FX is free software: you can redistribute it and/or modify
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
****************************************************************************/
package sporemodder.file.dbpf;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import sporemodder.file.filestructures.StreamWriter;
import javafx.concurrent.Task;
import sporemodder.HashManager;
import sporemodder.MessageManager;
import sporemodder.MessageManager.MessageType;
import sporemodder.file.Converter;
import sporemodder.file.ResourceKey;
import sporemodder.util.NameRegistry;
import sporemodder.util.Project.PackageSignature;

public class DBPFPackingTask extends Task<Void> {
	
	/** The folder with the contents that are being packed. */
	private File inputFolder;
	private File outputFile;
	
	private StreamWriter outputStream;
	
	/** The total progress (in [0, 1]). */
	private double progress = 0;

	private final PackageSignature packageSignature;

	private DBPFPacker packer;
	
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	private boolean noJavaFX = false;
	private int compressThreshold = -1;

	public DBPFPackingTask(File inputFolder, StreamWriter outputStream) {
		this.inputFolder = inputFolder;
		this.outputFile = null;
		this.outputStream = outputStream;
		this.packageSignature = PackageSignature.NONE;
	}

	@Override protected void updateMessage(String message) {
		if (!noJavaFX) {
			super.updateMessage(message);
		}
	}
	
	@Override protected void updateProgress(double workDone, double max) {
		if (!noJavaFX) {
			super.updateProgress(workDone, max);
		}
	}

	/**
	 * Sets the current file being processed. This is used when diagnosing errors.
	 * @param currentFile
	 */
	public void setCurrentFile(File currentFile) {
		packer.setCurrentFile(currentFile);
	}
	
	public void pause() {
		running.set(false);
	}

	private void pack() throws Exception {
		final HashManager hasher = HashManager.get();
		
		MessageManager.get().postMessage(MessageType.BeforeDbpfPack, this);
		
		//TODO support DBBF maybe?
		
		// Doesn't really make sense to let the user disable converters.
		List<Converter> converters = new ArrayList<>();
		// Reverse them so the most common ones (.prop, .rw4) are first
		Collections.reverse(converters);
		
		File[] folders = inputFolder.listFiles(new FileFilter() {

			@Override
			public boolean accept(File arg0) {
				return arg0.isDirectory();
			}
			
		});
		
		/** How much we increment the progress (in %) after every folder is completed. */
		double inc = 1.0 / folders.length;
		
		boolean alreadyHasPackageSignature = false;
		
		//hasher.getProjectRegistry().clear();
		hasher.setUpdateProjectRegistry(true);
		
		for (File folder : folders) {
			
			setCurrentFile(folder);
			
			String currentFolderName = folder.getName();
			int currentGroupID = hasher.getFileHash(currentFolderName);
			
			File[] files = folder.listFiles();
			
			for (File file : files) {
				// Ensure the task is not paused
				if (!running.get()) {
					synchronized (running) {
						while (!running.get()) {
							running.wait();
						}
					}
				}
				
				boolean bUsesConverter = false;
				
				String name = file.getName();
				file = getNestedFile(file, name, converters);
				setCurrentFile(file);
				
				for (Converter converter : converters) {
					if (converter.encode(file, packer, currentGroupID)) {
						bUsesConverter = true;
						break;
					}
				}
				
				// The converter must have written the data and added the DBPF item;
				// if there was no converter, we do it here
				if (!bUsesConverter) {
					
					String[] splits = name.split("\\.", 2);
					String currentExtension = splits.length > 1 ? splits[1] : "";
					
					int currentInstanceID = hasher.getFileHash(splits[0]);
					int currentTypeID = hasher.getTypeHash(currentExtension);
					
					byte[] currentInputData = Files.readAllBytes(file.toPath());
					
					packer.writeFile(new ResourceKey(currentGroupID, currentInstanceID, currentTypeID),
							currentInputData, currentInputData.length);
				}
			}
			
			if (!alreadyHasPackageSignature && currentGroupID == 0x40404000) {
				alreadyHasPackageSignature = true;
			}
			
			incProgress(inc);
		}
		
		writeNamesList();
		writePackageSignature(alreadyHasPackageSignature);
		
		MessageManager.get().postMessage(MessageType.OnDbpfPack, this);
	}

	@Override
	public Void call() throws Exception {
		
		try {
			DBPFPacker packer;
			if (outputStream != null) packer = new DBPFPacker(outputStream, false);
			else {
				if (!Files.isWritable(outputFile.getParentFile().toPath())) {
					throw new Exception("Access denied. Open the program using SporeModderFX.exe to have access permissions.");
				}
				packer = new DBPFPacker(outputFile);
			}
			
			this.packer = packer;
			packer.setCompressThreshold(compressThreshold);
			
			pack();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (packer != null) packer.close();
		}
		
		// Once done, we can disable updating the project registry
		HashManager.get().setUpdateProjectRegistry(false);
		
		return null;
	}

	private void incProgress(double increment) {
		progress += increment;
		updateProgress(progress, 1.0);
	}
	
	//TODO consider changing how nested files work
	private File getNestedFile(File file, String name, List<Converter> converters) {
		if (!file.isFile()) {
			for (Converter converter : converters) {
				if (converter.isEncoder(file)) return file;
			}
			File newFile = new File(file, name);
			if (!newFile.exists()) {
				//failedFiles.put(file, new UnsupportedOperationException("Couldn't find file " + name + " inside subfolder " + name));
				setCurrentFile(file);
				throw new UnsupportedOperationException("Couldn't find file " + name + " inside subfolder " + name);
			}
			file = newFile;
		}
		
		return file;
	}
	
	private void writeNamesList() throws IOException {
		NameRegistry reg = HashManager.get().getProjectRegistry();
		if (!reg.isEmpty()) {
			packer.writeFile(new ResourceKey(0x9C9059AE, 0xCC2F616F, 0x2B6CAB5F),
					stream -> reg.write(stream));
		}
	}
	final static int BUFFER_SIZE = 8192;
	
	private void writePackageSignature(boolean alreadyHasPackageSignature) throws IOException {
		
		if (packageSignature != PackageSignature.NONE && !alreadyHasPackageSignature) {
			
			DBPFItem item = packer.getTemporaryItem();
			StreamWriter stream = packer.getStream();
			
			item.name.setGroupID(0x40404000);
			item.name.setTypeID(0x00B1B104);
			item.name.setInstanceID(HashManager.get().getFileHash(packageSignature.getFileName()));
			
			item.chunkOffset = stream.getFilePointer();
			
			try (InputStream is = packageSignature.getInputStream()) {
				byte[] buffer = new byte[BUFFER_SIZE];
				int n;
				
				while ((n = is.read(buffer)) > 0) {
					stream.write(buffer, 0, n);
				}
			}
			
			item.isCompressed = false;
			item.memSize = (int) (stream.getFilePointer() - item.chunkOffset);
			item.compressedSize = item.memSize;
			
			packer.addFile(item);
		}
	}
}
