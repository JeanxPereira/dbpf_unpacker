package sporemodder.file.cell;

import java.io.File;
import java.io.PrintWriter;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import sporemodder.HashManager;
import sporemodder.file.Converter;
import sporemodder.file.DocumentException;
import sporemodder.file.ResourceKey;
import sporemodder.file.argscript.ArgScriptStream;
import sporemodder.file.dbpf.DBPFPacker;
import sporemodder.file.filestructures.FileStream;
import sporemodder.file.filestructures.StreamReader;
import sporemodder.file.filestructures.StreamWriter;


public class CellLookAlgorithmConverter implements Converter {
	
	private static final int TYPE_ID = 0xDBA35AE2;
	private static String extension = null;
	
	private boolean decode(StreamReader stream, File outputFile) throws Exception {
		CellLookAlgorithmFile unit = new CellLookAlgorithmFile();
		unit.read(stream);
		
		try (PrintWriter out = new PrintWriter(outputFile)) {
		    out.println(unit.toArgScript());
		}
		
		return true;
	}
	
	@Override
	public boolean decode(StreamReader stream, File outputFolder, ResourceKey key) throws Exception {
		return decode(stream, Converter.getOutputFile(key, outputFolder, "look_algorithm_t"));
	}

	@Override
	public boolean encode(File input, StreamWriter output) throws Exception {
		CellLookAlgorithmFile unit = new CellLookAlgorithmFile();
		ArgScriptStream<CellLookAlgorithmFile> stream = unit.generateStream();
		stream.setFolder(input.getParentFile());
		stream.setFastParsing(true);
		stream.process(input);
		unit.write(output);
		return true;
	}

	@Override
	public boolean encode(File input, DBPFPacker packer, int groupID) throws Exception {
		if (isEncoder(input)) {
			CellLookAlgorithmFile unit = new CellLookAlgorithmFile();
			ArgScriptStream<CellLookAlgorithmFile> stream = unit.generateStream();
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
			
			packer.writeFile(name, output -> unit.write(output));
			
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
		return file.isFile() && file.getName().endsWith("." + extension + ".look_algorithm_t");
	}

	@Override
	public String getName() {
		return "Cell Stage Look Algorithm File (." + HashManager.get().getTypeName(TYPE_ID) + ")";
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
