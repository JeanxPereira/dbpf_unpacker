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
package sporemodder.file.bitmaps;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import sporemodder.file.filestructures.FileStream;
import sporemodder.file.filestructures.StreamReader;
import sporemodder.file.filestructures.StreamWriter;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import sporemodder.HashManager;
import sporemodder.file.Converter;
import sporemodder.file.ResourceKey;
import sporemodder.file.dbpf.DBPFPacker;


public class BitmapConverter implements Converter {

	private static final List<Integer> SUPPORTED_TYPES = Arrays.asList(BitmapImage.TYPE_1BIT, BitmapImage.TYPE_8BIT, BitmapImage.TYPE_32BIT, BitmapImage.TYPE_48BIT);
	private static final List<String> EXTENSIONS = new ArrayList<>();
	
	private boolean decode(StreamReader stream, File outputFile, int type) throws Exception {
		BitmapImage image = new BitmapImage();
		image.type = type;
		image.read(stream);
		ImageIO.write(SwingFXUtils.fromFXImage(image.getImage(), null), "png", outputFile);
		return true;
	}
	
	@Override
	public boolean decode(StreamReader stream, File outputFolder, ResourceKey key) throws Exception {
		return decode(stream, Converter.getOutputFile(key, outputFolder, "png"), key.getTypeID());
	}

	@Override
	public boolean encode(File input, StreamWriter output) throws Exception {
		checkExtensions();
		for (int i = 0; i < SUPPORTED_TYPES.size(); ++i) {
			String fileName = input.getName();
			if (fileName.endsWith("." + EXTENSIONS.get(i) + ".png")) {
				
				try (FileInputStream fileStream = new FileInputStream(input)) {
					BitmapImage image = new BitmapImage(SUPPORTED_TYPES.get(i), new Image(fileStream));
					image.write(output);
					
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean encode(File input, DBPFPacker packer, int groupID) throws Exception {
		checkExtensions();
		for (int i = 0; i < SUPPORTED_TYPES.size(); ++i) {
			String fileName = input.getName();
			if (fileName.endsWith("." + EXTENSIONS.get(i) + ".png")) {
				
				try (FileInputStream fileStream = new FileInputStream(input)) {
					BitmapImage image = new BitmapImage(SUPPORTED_TYPES.get(i), new Image(fileStream));
					
					String[] splits = fileName.split("\\.", 2);
					
					ResourceKey name = packer.getTemporaryName();
					name.setInstanceID(HashManager.get().getFileHash(splits[0]));
					name.setGroupID(groupID);
					name.setTypeID(image.type);
					
					packer.writeFile(name, stream -> image.write(stream));
					
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public boolean isDecoder(ResourceKey key) {
		return SUPPORTED_TYPES.contains(key.getTypeID());
	}

	private void checkExtensions() {
		if (EXTENSIONS.isEmpty()) {
			for (int i : SUPPORTED_TYPES) EXTENSIONS.add(HashManager.get().getTypeName(i));
		}
	}
	
	@Override
	public boolean isEncoder(File file) {
		checkExtensions();
		if (!file.isFile()) return false;
		String fileName = file.getName();
		for (String extension : EXTENSIONS) {
			if (fileName.endsWith("." + extension + ".png")) return true;
		}
		return false;
	}

	@Override
	public String getName() {
		return "Raw Bitmap (*.bitImage)";
	}

	@Override
	public boolean isEnabledByDefault() {
		return false;
	}

	@Override
	public int getOriginalTypeID(String extension) {
		for (int i = 0; i < SUPPORTED_TYPES.size(); ++i) {
			if (extension.startsWith('.' + EXTENSIONS.get(i))) return SUPPORTED_TYPES.get(i);
		}
		return -1;
	}

}
