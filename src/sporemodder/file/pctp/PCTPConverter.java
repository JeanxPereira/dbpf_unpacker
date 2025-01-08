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
package sporemodder.file.pctp;

import java.io.File;
import java.io.PrintWriter;

import sporemodder.file.filestructures.FileStream;
import sporemodder.file.filestructures.StreamReader;
import sporemodder.file.filestructures.StreamWriter;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import sporemodder.HashManager;
import sporemodder.file.Converter;
import sporemodder.file.DocumentException;
import sporemodder.file.ResourceKey;
import sporemodder.file.argscript.ArgScriptStream;
import sporemodder.file.dbpf.DBPFPacker;


public class PCTPConverter implements Converter {

	private static String extension = null;
	
	private boolean decode(StreamReader stream, File outputFile) throws Exception {
		PCTPUnit pctp = new PCTPUnit();
		pctp.read(stream);
		
		try (PrintWriter out = new PrintWriter(outputFile)) {
		    out.println(pctp.toArgScript());
		}
		
		return true;
	}
	
	@Override
	public boolean decode(StreamReader stream, File outputFolder, ResourceKey key) throws Exception {
		return decode(stream, Converter.getOutputFile(key, outputFolder, "pctp_t"));
	}

	@Override
	public boolean encode(File input, StreamWriter output) throws Exception {
		PCTPUnit pctp = new PCTPUnit();
		ArgScriptStream<PCTPUnit> stream = pctp.generateStream();
		stream.setFolder(input.getParentFile());
		stream.setFastParsing(true);
		stream.process(input);
		pctp.write(output);
		return true;
	}

	@Override
	public boolean encode(File input, DBPFPacker packer, int groupID) throws Exception {
		if (isEncoder(input)) {
			PCTPUnit pctp = new PCTPUnit();
			ArgScriptStream<PCTPUnit> stream = pctp.generateStream();
			stream.setFolder(input.getParentFile());
			stream.setFastParsing(true);
			stream.process(input);
			
			if (!stream.getErrors().isEmpty()) {
				throw new DocumentException(stream.getErrors().get(0));
			}
			
			String[] splits = input.getName().split("\\.", 2);
			
			ResourceKey name = packer.getTemporaryName();
			name.setInstanceID(HashManager.get().getFileHash(splits[0]));
			name.setGroupID(groupID);
			name.setTypeID(0x7C19AA7A);
			
			packer.writeFile(name, output -> pctp.write(output));
			
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isDecoder(ResourceKey key) {
		return key.getTypeID() == 0x7C19AA7A;
	}

	private void checkExtensions() {
		if (extension == null) {
			extension = HashManager.get().getTypeName(0x7C19AA7A);
		}
	}
	
	@Override
	public boolean isEncoder(File file) {
		checkExtensions();
		return file.isFile() && file.getName().endsWith("." + extension + ".pctp_t");
	}

	@Override
	public String getName() {
		return "Part Capabilities File (." + HashManager.get().getTypeName(0x7C19AA7A) + ")";
	}

	@Override
	public boolean isEnabledByDefault() {
		return true;
	}

	@Override
	public int getOriginalTypeID(String extension) {
		checkExtensions();
		return 0x7C19AA7A;
	}

}
