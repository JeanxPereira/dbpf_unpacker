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
package sporemodder;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import sporemodder.util.DownloadTask;
import sporemodder.util.VersionInfo;

public class UpdateManager {

	/**
     * Pattern to present day in ISO-8601.
     */
    public static final String FORMAT_ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    
    public static final SimpleDateFormat FORMAT_ISO = new SimpleDateFormat(FORMAT_ISO_PATTERN);
    
    /**
     * The time zone we're in.
     */
    public static final TimeZone TIMEZONE = TimeZone.getTimeZone("UTC");
    
    public static final VersionInfo versionInfo = VersionInfo.fromString(Launcher.VERSION);
    
    public static UpdateManager get() {
    	return null;
    }
    
    public VersionInfo getVersionInfo() {
    	return versionInfo;
    }
    
    /**
     * Checks whether there is any update available, and if it is, it asks the user whether to download it.
     * Returns true if he program can continue, false if the program should abort and execute the update.
     * @return
     */
    public boolean checkUpdate() {
    	try {
			JSONObject githubRelease = getLastRelease("Emd4600", "SporeModder-FX");
			
			VersionInfo newVersion = VersionInfo.fromString(githubRelease.getString("tag_name"));
			if (newVersion.isGreaterThan(versionInfo)) {
				return showUpdateDialog(githubRelease);
			}
			
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
    	return true;
    }
    
    private boolean showUpdateDialog(JSONObject release) {
    	return false;
    }

	private static String executeGet(String request) throws IOException {
		
		return null;
	}
	
	public JSONObject getLastRelease(String owner, String repo) throws IOException, ParseException {
		return new JSONObject(new JSONTokener(executeGet("/repos/" + owner + "/" + repo + "/releases/latest")));
	}
}
