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
package sporemodder.file.prop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;

import sporemodder.file.filestructures.StreamReader;
import sporemodder.file.filestructures.StreamWriter;
import sporemodder.file.DocumentException;

public class PropertyBool extends BaseProperty {
	
	public static final int TYPE_CODE = 0x0001;
	public static final String KEYWORD = "bool";
	public static final int ARRAY_SIZE = 1;

	private boolean[] values;
	
	public PropertyBool() {
		super(TYPE_CODE, 0);
	}
	
	public PropertyBool(boolean value) {
		super(TYPE_CODE, 0);
		this.values = new boolean[] {value};
	}
	
	public PropertyBool(boolean ... values) {
		super(TYPE_CODE, 0, ARRAY_SIZE, values.length);
		this.values = values;
	}
	
	public PropertyBool(List<Boolean> values) {
		super(TYPE_CODE, 0, ARRAY_SIZE, values.size());
		this.values = new boolean[values.size()];
		for (int i = 0; i < this.values.length; i++) {
			this.values[i] = values.get(i);
		}
	}
	
	public boolean[] getValues() {
		return values;
	}
	
	@Override
	public void read(StreamReader stream, int itemCount) throws IOException {
		values = new boolean[itemCount];
		stream.readBooleans(values);
	}
	
	@Override
	public void write(StreamWriter stream) throws IOException {
		for (boolean value : values) {
			stream.writeBoolean(value);
		}
	}
	
	public static void fastConvertXML(StreamWriter stream, Attributes attributes, String text) throws IOException {
		stream.writeBoolean(Boolean.parseBoolean(text));
	}
}
