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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import sporemodder.file.filestructures.FileStream;
import sporemodder.file.filestructures.MemoryStream;
import sporemodder.file.filestructures.StreamReader;
import sporemodder.HashManager;
import sporemodder.file.Converter;
import sporemodder.file.ResourceKey;

public class DBPFUnpacker {
	
	@FunctionalInterface
	public static interface DBPFItemFilter {
		public boolean filter(DBPFItem item);
	}
	
	/** The estimated progress (in [0, 1]) that reading the index takes. */ 
	private static final double INDEX_PROGRESS = 0.15;

	/** The list of input DBPF files, in order of priority. */
	private final List<File> inputFiles = new ArrayList<File>();
	
	/** Alternative input, an StreamReader. */
	private final StreamReader inputStream;
	
	/** The list of input DBPF files that could not be unpacked (because they didn't exist). */
	private final List<File> failedDBPFs = new ArrayList<File>();
	
	/** The folder where all the contents will be written. */
	private File outputFolder;
	
	/** We will keep all files that couldn't be converted here, so that we can keep unpacking the DBPF. */
	private final HashMap<DBPFItem, Exception> exceptions = new HashMap<DBPFItem, Exception>();
	
	/** All the converters used .*/
	private final List<Converter> converters;
	
	/** How much time the operation took, in milliseconds. */
	private long ellapsedTime;
	
	/** An optional filter that defines which items should be unpacked (true) and which shouldn't (false). */
	private DBPFItemFilter itemFilter;

	public DBPFUnpacker(File inputFile, File outputFolder, List<Converter> converters) {
		this.inputFiles.add(inputFile);
		this.outputFolder = outputFolder;
		this.converters = converters;
		this.inputStream = null;
	}
	
	/**
	 * Returns a list of all the converters that will be used when unpacking files.
	 * On every file, if the converter {@link Converter.isDecoder()} method returns true, it will be used to decode the file.
	 * @return
	 */
	public List<Converter> getConverters() {
		return converters;
	}

	/**
	 * Returns the output folder where the unpacked files will be written.
	 * @return
	 */
	public File getOutputFolder() {
		return outputFolder;
	}
	
	/**
	 * Sets a method that decides which items are unpacked and which are ignored.
	 * @param itemFilter
	 */
	public void setItemFilter(DBPFItemFilter itemFilter) {
		this.itemFilter = itemFilter;
	}
	
	private static void findNamesFile(List<DBPFItem> items, StreamReader in, HashManager hasher) throws IOException {
		int group = hasher.getFileHash("sporemaster");
		int name = hasher.getFileHash("names");
		
		for (DBPFItem item : items) {
			if (item.name.getGroupID() == group && item.name.getInstanceID() == name) {
				try (ByteArrayInputStream arrayStream = new ByteArrayInputStream(item.processFile(in).getRawData());
						BufferedReader reader = new BufferedReader(new InputStreamReader(arrayStream))) {
					hasher.getProjectRegistry().read(reader);
				}
			}
		}
	}

	private void unpackStream(StreamReader packageStream, HashMap<Integer, List<ResourceKey>> writtenFiles) throws IOException, InterruptedException {
		HashManager hasher = new HashManager();
		hasher.initialize(null);

		//updateMessage("Reading file index...");
		
		DatabasePackedFile header = new DatabasePackedFile();
		header.readHeader(packageStream);
		header.readIndex(packageStream);
		
		DBPFIndex index = header.index;
		index.readItems(packageStream, header.indexCount, header.isDBBF);
		
		incProgress(INDEX_PROGRESS / inputFiles.size());
		//updateMessage("Unpacking files...");
		
		double inc = ((1.0 - INDEX_PROGRESS) / header.indexCount) / inputFiles.size();
		
		//First search sporemaster/names.txt, and use it if it exists
		hasher.getProjectRegistry().clear();
		findNamesFile(index.items, packageStream, hasher);
		
		for (DBPFItem item : index.items) {
			// Ensure the task is not paused
			//ensureRunning();
			
			if (itemFilter != null && !itemFilter.filter(item)) continue;
			
			int groupID = item.name.getGroupID();
			int instanceID = item.name.getInstanceID();
			
			if (writtenFiles != null) {
				List<ResourceKey> list = writtenFiles.get(groupID);
				if (list != null) {
					boolean skipFile = false;
					for (ResourceKey key : list) {
						if (key.isEquivalent(item.name)) {
							skipFile = true;
							break;
						}
					}
					if (skipFile) continue;
				}
			}
			
			String fileName = hasher.getFileName(instanceID);
			
			// skip autolocale files
			if (groupID == 0x02FABF01 && fileName.startsWith("auto_")) {
				continue;
			}
			
			
			File folder = new File(outputFolder, hasher.getFileName(groupID));
			folder.mkdir();
			
			try (MemoryStream dataStream = item.processFile(packageStream)) {
				
				// Has the file been converted?
				boolean isConverted = false;
				
				// Do not convert editor packages
				if (groupID == 0x40404000 && item.name.getTypeID() == 0x00B1B104) {
				}
				else {
					try {
						for (Converter converter : converters) {
							if (converter.isDecoder(item.name)) {
								
								if (converter.decode(dataStream, folder, item.name)) {
									isConverted = true;
									break;
								}
								else {
									// throw new IOException("File could not be converted.");
									// We could throw an error here, but it is not appropriate:
									// some files cannot be converted but did not necessarily have an error,
									// for example trying to convert a non-texture rw4. So we jsut keep searching
									// for another converter or write the raw file.s
									continue;
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						exceptions.put(item, e);
						// Handling the exception here will make it write the unconverted file
					}
				}
				
				if (!isConverted) {
					// If it hasn't been converted, just write the file straight away.
					
					String name = hasher.getFileName(item.name.getInstanceID()) + "." + hasher.getTypeName(item.name.getTypeID());
					dataStream.writeToFile(new File(folder, name));
				}
				
				if (writtenFiles != null) {
					List<ResourceKey> list = writtenFiles.get(groupID);
					if (list == null) {
						list = new ArrayList<ResourceKey>();
						writtenFiles.put(groupID, list);
					}
					list.add(item.name);
				}
			}
			catch (Exception e) {
				exceptions.put(item, e);
			}
			
			incProgress(inc);
		}
		
		// Remove the extra names; if they need to be used, loading the project will load them as well
		hasher.getProjectRegistry().clear();
	}
	
	public Exception call() throws Exception {
		
		long initialTime = System.currentTimeMillis();
		
		if (inputStream != null) {
			unpackStream(inputStream, null);
		}
		else {
			final HashMap<Integer, List<ResourceKey>> writtenFiles = new HashMap<Integer, List<ResourceKey>>();
			boolean checkFiles = inputFiles.size() > 1;  // only check already existing files if we are unpacking more than one package at once
			
			for (File inputFile : inputFiles) {
				if (!inputFile.exists()) {
					incProgress(100.0f / inputFiles.size());
					failedDBPFs.add(inputFile);
					continue;
				}
				
				for (Converter converter : converters) converter.reset();
				
				try (StreamReader packageStream = new FileStream(inputFile, "r"))  {
					unpackStream(packageStream, checkFiles ? writtenFiles : null);
				}
				catch (Exception e) {
					return e;
				}
			}
		}
		
		ellapsedTime = System.currentTimeMillis() - initialTime;

		return null;
	}
	
	/**
	 * Returns a Map with all the items that could not be unpacked/converted, mapped to the exception that caused that error.
	 * @return
	 */
	public HashMap<DBPFItem, Exception> getExceptions() {
		return exceptions;
	}

	private void incProgress(double increment) {
		//progress += increment;
		//updateProgress(progress, 1.0);
	}
	
	/**
	 * When unpacking multiple packages at once, returns a list of all package files that couldn't be unpacked.
	 * @return
	 */
	public List<File> getFailedDBPFs() {
		return failedDBPFs;
	}

	public static void main(String[] args) throws Exception {
		// Note: The output folder must already exist
		File inputFile = new File("/home/vitor/Projects/recap/darkspore/5.3.0.127/Data/ServerData.package");
		File ooutputFile = new File("/home/vitor/Projects/recap/darkspore/5.3.0.127/Data/ServerData.package.out");
		List<Converter> converters = List.of(new DBPFConverter());
		var unpacker = new DBPFUnpacker(inputFile, ooutputFile, converters);
		unpacker.call();
	}
}
