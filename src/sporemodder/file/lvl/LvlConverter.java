package sporemodder.file.lvl;

import java.io.File;
import java.io.PrintWriter;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import sporemodder.HashManager;
import sporemodder.ProjectManager;
import sporemodder.file.Converter;
import sporemodder.file.DocumentException;
import sporemodder.file.ResourceKey;
import sporemodder.file.argscript.ArgScriptStream;
import sporemodder.file.dbpf.DBPFPacker;
import sporemodder.file.filestructures.FileStream;
import sporemodder.file.filestructures.StreamReader;
import sporemodder.file.filestructures.StreamWriter;
import sporemodder.util.ProjectItem;

public class LvlConverter implements Converter {
	
	private static final int TYPE_ID = 0x47b8300;
	private static String extension = null;
	
	private boolean decode(StreamReader stream, File outputFile) throws Exception {
		LevelDefinition level = new LevelDefinition();
		level.read(stream);
		
		try (PrintWriter out = new PrintWriter(outputFile)) {
		    out.println(level.toArgScript());
		}
		
		return true;
	}
	
	@Override
	public boolean decode(StreamReader stream, File outputFolder, ResourceKey key) throws Exception {
		return decode(stream, Converter.getOutputFile(key, outputFolder, "lvl_t"));
	}

	@Override
	public boolean encode(File input, StreamWriter output) throws Exception {
		LevelDefinition level = new LevelDefinition();
		ArgScriptStream<LevelDefinition> stream = level.generateStream();
		stream.setFolder(input.getParentFile());
		stream.setFastParsing(true);
		stream.process(input);
		level.write(output);
		return true;
	}

	@Override
	public boolean encode(File input, DBPFPacker packer, int groupID) throws Exception {
		if (isEncoder(input)) {
			LevelDefinition level = new LevelDefinition();
			ArgScriptStream<LevelDefinition> stream = level.generateStream();
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
			name.setTypeID(TYPE_ID);
			
			packer.writeFile(name, output -> level.write(output));
			
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isDecoder(ResourceKey key) {
		return key.getTypeID() == TYPE_ID;
	}

	private void checkExtensions() {
		if (extension == null) {
			extension = HashManager.get().getTypeName(TYPE_ID);
		}
	}
	
	@Override
	public boolean isEncoder(File file) {
		checkExtensions();
		return file.isFile() && file.getName().endsWith("." + extension + ".lvl_t");
	}

	@Override
	public String getName() {
		return "Level Definition File (." + HashManager.get().getTypeName(TYPE_ID) + ")";
	}

	@Override
	public boolean isEnabledByDefault() {
		return true;
	}

	@Override
	public int getOriginalTypeID(String extension) {
		checkExtensions();
		return TYPE_ID;
	}

}
