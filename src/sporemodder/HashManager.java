/****************************************************************************
* Copyright (C) 2018 Eric Mor
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

package sporemodder;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

import sporemodder.util.NameRegistry;

/**
 * A class used to control hashes (hexadecimal 32-bit integers used as IDs) and everything related to them,
 * including name registries, name parsing, etc
 */
public class HashManager {
	
	/**
	 * Returns the current instance of the HashManager class.
	 */
	public static HashManager get() {
		return null;
	}

	/** The symbols used to print floating point values. This decides the decimal separator: we must always use '.' to avoid language problems. */
	private DecimalFormatSymbols decimalSymbols;
	/** The format that decides the number of decimals used to print floating point values. */
	private String decimalFormat;
	/** The default decimal format object used to print float values. */
	private DecimalFormat defaultDecimalFormat;
	
	/** The original registry used to look for instance and group IDs; it is read from reg_file.txt */
	private final NameRegistry originalFileRegistry = new NameRegistry(this, "File Names", "reg_file.txt");
	/** The original registry used to look for type IDs; it is read from reg_type.txt */
	private final NameRegistry originalTypeRegistry = new NameRegistry(this, "Types", "reg_type.txt");
	/** The original registry used to look for property IDs; it is read from reg_property.txt */
	private final NameRegistry originalPropRegistry = new NameRegistry(this, "Properties", "reg_property.txt");
	
	/** The registry used to look for simulator attribute IDs; it is read from reg_simulator.txt */
	private final NameRegistry simulatorRegistry = new NameRegistry(this, "Simulator Attributes", "reg_simulator.txt");
	
	/** The registry used to look for instance and group IDs; it is read from reg_file.txt */
	private NameRegistry fileRegistry = originalFileRegistry;
	/** The registry used to look for type IDs; it is read from reg_type.txt */
	private NameRegistry typeRegistry = originalTypeRegistry;
	/** The registry used to look for property IDs; it is read from reg_property.txt */
	private NameRegistry propRegistry = originalPropRegistry;
	
	/** A temporary registry that keeps names used by a certain Project. This is only updated when doing certain actions, like packing the mod. */
	private final NameRegistry projectRegistry = new NameRegistry(this, "Names used by the project", "names.txt");
	private boolean updateProjectRegistry;

	/** A temporary registry that keeps track of all types/properties names used; can be used to import old projects without losing information. */
	private NameRegistry extraRegistry;

	private final HashMap<String, NameRegistry> registries = new HashMap<String, NameRegistry>();
	
	public void initialize() {
		decimalSymbols = new DecimalFormatSymbols(Locale.getDefault());
		decimalSymbols.setDecimalSeparator('.');
		decimalFormat = "#.#######";
		defaultDecimalFormat = new DecimalFormat(decimalFormat, decimalSymbols);
		defaultDecimalFormat.setNegativePrefix("-");

		PathManager pathManager = new PathManager();
		pathManager.initialize();
		try {
			fileRegistry.read(pathManager.getProgramFile(fileRegistry.getFileName()));
		} catch (Exception e) {
			throw new RuntimeException("The file name registry (reg_file.txt) is corrupt or missing.");
		}
		try {
			typeRegistry.read(pathManager.getProgramFile(typeRegistry.getFileName()));
		} catch (Exception e) {
			throw new RuntimeException("The types registry (reg_type.txt) is corrupt or missing.");
		}
		try {
			propRegistry.read(pathManager.getProgramFile(propRegistry.getFileName()));
		} catch (Exception e) {
			throw new RuntimeException("The property registry (reg_property.txt) is corrupt or missing.");
		}
		try {
			simulatorRegistry.read(pathManager.getProgramFile(simulatorRegistry.getFileName()));
			simulatorRegistry.read(pathManager.getProgramFile("reg_simulator_stub.txt"));
		} catch (Exception e) {
			throw new RuntimeException("The simulator attributes registry (reg_simulator.txt or reg_simulator_stub.txt) is corrupt or missing.");
		}

        registries.put(fileRegistry.getFileName(), fileRegistry);
		registries.put(typeRegistry.getFileName(), typeRegistry);
		registries.put(propRegistry.getFileName(), propRegistry);
		registries.put(simulatorRegistry.getFileName(), simulatorRegistry);
		registries.put(projectRegistry.getFileName(), projectRegistry);
	}

	public NameRegistry getProjectRegistry() {
		return projectRegistry;
	}

	/**
	 * Calculates the 32-bit FNV hash used by Spore for the given string.
	 * It is case-insensitive: the string is converted to lower-case before calculating the hash.
	 * @param string The string whose hash will be calculated.
	 * @return The equivalent hash.
	 */
	public int fnvHash(String string) {
		char[] lower = string.toLowerCase().toCharArray();
        int rez = 0x811C9DC5;
        for (int i = 0; i < lower.length; i++) {
        	rez *= 0x1000193;
        	rez ^= lower[i];
        }
        return rez;
	}
	
	/**
	 * Returns a string formatted like <code>0xXXXXXXXX</code>, replacing the X with the hexadecimal
	 * representation of the given integer. For example, the number 7234234 would return <code>0x006e62ba</code>.
	 * @param num The integer that will be converted into an hexadecimal string.
	 * @return
	 */
	public String hexToString(int num) {
		return "0x" + String.format("%8s", Integer.toHexString(num)).replace(' ', '0');
	}
	
	/**
	 * Same as {@link #hexToString(int)}, but this gives the hexadecimal number in uppercase letters.
	 * @param num The integer that will be converted into an hexadecimal string.
	 * @return
	 */
	public String hexToStringUC(int num) {
		return "0x" + String.format("%8s", Integer.toHexString(num).toUpperCase()).replace(' ', '0');
	}
	
	/**
	 * Returns the equivalent 32-bit signed integer parsed from the given string. The following formats are allowed:
	 *  <li><code>5309</code>: It is parsed as a decimal number, so 5309 is returned.
	 *  <li><code>0x6e62ba</code>: It is parsed as a hexadecimal number ignoring the <i>0x</i>, so 7234234 is returned.
	 *  <li><code>#6e62ba</code>: It is parsed as a hexadecimal number ignoring the <i>#</i>, so 7234234 is returned.
	 *  <li><code>b10011</code>: It is parsed as a binary number ignoring the <i>b</i>, so 19 is returned.
	 *  <li><code>$Creature</code>: The hash of '<i>Creature</i>' is returned, using the {@link #getFileHash(String)} method.
	 * @param str The string to decode into a number.
	 * @return The equivalent 32-bit signed integer (<code>int32</code>).
	 */
	public int int32(String str) {
		int result = 0;
		
		if (str == null || str.length() == 0) {
			return 0;
		}
		
		if (str.startsWith("0x")) {
			result = Integer.parseUnsignedInt(str.substring(2), 16);
		}
		else if (str.startsWith("#")) {
			result = Integer.parseUnsignedInt(str.substring(1), 16);
		}
		else if (str.startsWith("$")) {
			result = getFileHash(str.substring(1));
		}
		else if (str.endsWith("b")) {
			result = Integer.parseUnsignedInt(str.substring(0, str.length() - 1), 2);
		}
		else {
			result = Integer.parseInt(str);
		}
		
		return result;
	}

	/**
	 * Returns the string that represents the given hash, taken from the instance and group IDs registry (reg_file.txt).
	 * File hashes are different to the rest because they have support for <i>aliases</i>: Only names that end with the symbol <i>~</i> can be 
	 * assigned to any hash; the rest of the names are always assigned to their equivalent FNV hash.
	 * <li>If the name is not found in the registry, the hexadecimal representation of the hash will be returned, such as <code>0x006E62BA</code>.
	 */
	public String getFileName(int hash) {
		String str = getFileNameOptional(hash);
		if (str != null) {
			return str;
		} else {
			return hexToStringUC(hash);
		}
	}
	
	// Returns null if no name is found
	private String getFileNameOptional(int hash) {
		String str = fileRegistry.getName(hash);
		if (str != null) {
			return str;
		} else {
			return projectRegistry.getName(hash);
		}
	}
	
	/**
	 * Returns the string that represents the given hash, taken from the type IDs registry (reg_type.txt).
	 * <li>If the name is not found in the registry, the hexadecimal representation of the hash will be returned, such as <code>0x006E62BA</code>.
	 */
	public String getTypeName(int hash) {
		String str = typeRegistry.getName(hash);
		if (str == null && extraRegistry != null) {
			str = extraRegistry.getName(hash);
		}
		if (str != null) {
			return str;
		} else {
			return hexToStringUC(hash);
		}
	}

	/**
	 * Returns the integer that represents the hash of the given name, taken from the group and instance IDs registry (reg_file.txt).
	 * File hashes are different to the rest because they have support for <i>aliases</i>: Only names that end with the symbol <i>~</i> can be 
	 * assigned to any hash; the rest of the names are always assigned to their equivalent FNV hash.
	 * <li>If the name is not found in the registry, its FNV hash is returned.
	 * <li>If the string begins with <code>0x</code> or <code>#</code>, it will be interpreted as a 8-digit or less hexadecimal number.
	 * <li>If the input string is null, this method returns -1.
	 */
	public int getFileHash(String name) {
		if (name == null) {
			return -1;
		}
		if (name.startsWith("#")) {
			return Integer.parseUnsignedInt(name.substring(1), 16);
		} 
		else if (name.startsWith("0x")) {
			return Integer.parseUnsignedInt(name.substring(2), 16);
		} 
		else {
			if (!name.endsWith("~")) {
				int hash = fnvHash(name);
				if (updateProjectRegistry) {
					projectRegistry.add(name, hash);
				}
				return hash;
			} 
			else {
				String lc = name.toLowerCase();
				Integer i = fileRegistry.getHash(lc);
				if (i == null) {
					i = projectRegistry.getHash(lc);
				}
				if (i == null) {
					throw new IllegalArgumentException("Unable to find " + name + " hash.  It does not exist in the reg_file registry.");
				}
				if (updateProjectRegistry) {
					projectRegistry.add(name, i);
				}
				return i;
			}
		}
	}
	
	/**
	 * Returns the integer that represents the hash of the given name, taken from the type IDs registry (reg_type.txt).
	 * <li>If the name is not found in the registry, its FNV hash is returned.
	 * <li>If the string begins with <code>0x</code> or <code>#</code>, it will be interpreted as a 8-digit or less hexadecimal number.
	 * <li>If the input string is null, this method returns -1.
	 */
	public int getTypeHash(String name) {
		if (name == null) {
			return -1;
		}
		if (name.startsWith("#")) {
			return Integer.parseUnsignedInt(name.substring(1), 16);
		}
		else if (name.startsWith("0x")) {
			return Integer.parseUnsignedInt(name.substring(2), 16);
		} 
		else {
			Integer i = typeRegistry.getHash(name);
			if (i == null && extraRegistry != null) {
				extraRegistry.add(name, fnvHash(name));
			}
			return i == null ? fnvHash(name) : i;
		}
	}
}
