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
package sporemodder.view.editors;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import sporemodder.EditorManager;
import sporemodder.HashManager;
import sporemodder.ProjectManager;
import sporemodder.UIManager;
import sporemodder.file.DocumentError;
import sporemodder.file.DocumentFragment;
import sporemodder.file.argscript.ArgScriptLine;
import sporemodder.file.argscript.ArgScriptStream;
import sporemodder.file.argscript.ArgScriptStream.HyperlinkData;
import sporemodder.util.ProjectItem;
import sporemodder.view.StatusBar.Status;
import sporemodder.view.syntax.SyntaxHighlighter;

/**
 * An editor that is backed by an ArgScript stream. This means that the editor will use the ArgScript syntax highlighting,
 * show the errors of the stream in tooltips, etc
 */
public abstract class ArgScriptEditor<T> extends TextEditor {
	
	// A class we need to store additional information
	private static class ErrorInfo {
		DocumentError error;
		
		int position;
		int length;
	}
	
	/** The current errors, used in tooltips. */
	private final List<ErrorInfo> errors = new ArrayList<ErrorInfo>();
	
	protected HyperlinkData currentHyperlink;
	
	protected ArgScriptStream<T> stream;
	
	private double mouseX;
	private double mouseY;
	
	public ArgScriptEditor() {
		super();
		
		// Generate tooltips for the errors
		getTooltipFactories().add((text, event) -> {
			int index = event.getCharacterIndex();
			
			for (ErrorInfo error : errors) {
				if (error.position <= index && index < error.position + error.length) {
					return error.error.getMessage();
				}
			}
			
			return null;
		});
		
		setSyntaxHighlighting((text, syntax) -> {
			if (stream != null) {
				onStreamParse();
				
				stream.process(getText());
				SyntaxHighlighter streamSyntax = stream.getSyntaxHighlighter();
				stream.addErrorsSyntax();
				syntax.addExtras(streamSyntax, false);
				
				setErrorInfo(streamSyntax);
			}
		});
		
		getCodeArea().addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
			mouseX = event.getScreenX();
			mouseY = event.getScreenY();
			
			updateHyperlink(event.isControlDown());
		});
		getCodeArea().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			updateHyperlink(event.isControlDown());
		});
		getCodeArea().addEventFilter(KeyEvent.KEY_RELEASED, event -> {
			updateHyperlink(event.isControlDown());
		});
		
		getCodeArea().addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
			if (currentHyperlink != null) {
				int linePos = stream.getSyntaxHighlighter().getLinePosition(currentHyperlink.line);
				getCodeArea().getCharacterBoundsOnScreen(linePos + currentHyperlink.start, linePos + currentHyperlink.end).ifPresent(bounds -> {
					if (bounds.contains(mouseX, mouseY)) {
						onHyperlinkAction(currentHyperlink);
					}
				});
			}
		});
	}
	
	protected void onHyperlinkAction(HyperlinkData hyperlink) {
	}
	
	protected boolean hyperlinkOpenFile(String path) {
		try {
			ProjectItem item = ProjectManager.get().getItem(path);
			ProjectManager.get().expandToItem(item);
			EditorManager.get().loadFile(item);
			EditorManager.get().moveFileToNewTab(item);	
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	protected void hyperlinkOpenFile(String[] names) {
		// group, instance, type
		
		String path = null;
		
		if (names[0] != null) {
			path = names[0] + "\\" + names[1];
		} else {
			path = HashManager.get().getFileName(0) + "\\" + names[1];
		}
		
		// By default, try .prop/.soundProp files
		String prop = HashManager.get().getTypeName(0x00B1B104);
		String soundProp = HashManager.get().getTypeName(0x02B9F662);
		
		if (names[2] != null) {
			path = path + '.' + names[2];
			
			if (names[2].equals(prop) || names[2].equals(soundProp)) {
				path = path + ".prop_t";
			}
		} else {
			File file = getFile();
			
			if (file.getName().contains(soundProp)) {
				path = path + '.' + soundProp;
			} else {
				path = path + '.' + prop;
			}
			
			path = path + ".prop_t";
		}
		
		try {
			ProjectItem item = ProjectManager.get().getItem(path);
			ProjectManager.get().expandToItem(item);
			EditorManager.get().loadFile(item);
			EditorManager.get().moveFileToNewTab(item);
		} 
		catch (Exception e) {
			// Maybe it's a converted .rw4.dds
			if (path.endsWith('.' + HashManager.get().getTypeName(0x2F4E681B))) {
				try {
					path = path + ".dds";
					ProjectItem item = ProjectManager.get().getItem(path);
					ProjectManager.get().expandToItem(item);
					EditorManager.get().loadFile(item);
					EditorManager.get().moveFileToNewTab(item);
				}
				catch (IOException e1) {
					// Just ignore it
				}
			}
		}
	}
	
	private void updateHyperlink(boolean mustFindHyperlink) {
		HyperlinkData oldHyperlink = currentHyperlink;
		currentHyperlink = null;
		
		if (mustFindHyperlink) {
			findHyperlink();
		}
		
		if (oldHyperlink != null && oldHyperlink != currentHyperlink) {
			int linePos = stream.getSyntaxHighlighter().getLinePosition(oldHyperlink.line);
			getCodeArea().clearStyle(linePos+oldHyperlink.start, linePos+oldHyperlink.end);
		}
		if (currentHyperlink != null) {
			int linePos = stream.getSyntaxHighlighter().getLinePosition(currentHyperlink.line);
			getCodeArea().setStyle(linePos+currentHyperlink.start, linePos+currentHyperlink.end, Collections.singleton("hyperlink"));
		}
	}
	
	private void findHyperlink() {
		List<HyperlinkData> hyperlinks = stream.getHyperlinkData();
		
		for (HyperlinkData data : hyperlinks) {
			if (currentHyperlink != null) break;
			
			int linePos = stream.getSyntaxHighlighter().getLinePosition(data.line);
			getCodeArea().getCharacterBoundsOnScreen(linePos + data.start, linePos + data.end).ifPresent(bounds -> {
				if (bounds.contains(mouseX, mouseY)) {
					currentHyperlink = data;
				}
			});
		}
	}
	
	public ArgScriptStream<T> getStream() {
		return stream;
	}
	
	private void setErrorInfo(SyntaxHighlighter syntax) {
		errors.clear();
		
		// Store some information about the errors for tooltips
		List<DocumentError> documentErrors = stream.getErrors();
		for (DocumentError error : documentErrors) {
			int lineStart = syntax.getLinePosition(error.getLine());
			int startPosition = lineStart + error.getStartPosition();
			int endPosition = lineStart + error.getEndPosition();
			
			ErrorInfo errorInfo = new ErrorInfo();
			errorInfo.error = error;
			errorInfo.position = startPosition;
			errorInfo.length = endPosition - startPosition;
			
			errors.add(errorInfo);
		}
		
		List<DocumentError> documentWarnings = stream.getWarnings();
		for (DocumentError error : documentWarnings) {
			int lineStart = syntax.getLinePosition(error.getLine());
			int startPosition = lineStart + error.getStartPosition();
			int endPosition = lineStart + error.getEndPosition();
			
			ErrorInfo errorInfo = new ErrorInfo();
			errorInfo.error = error;
			errorInfo.position = startPosition;
			errorInfo.length = endPosition - startPosition;
			
			errors.add(errorInfo);
		}
	
		// Errors are ordered by lines, but not by position; fix that here
		Collections.sort(errors, new Comparator<ErrorInfo>() {
			@Override
			public int compare(ErrorInfo obj1, ErrorInfo obj2) {
				return obj1.position - obj2.position;
			}
		});
		
		if (documentErrors.isEmpty()) {
			UIManager.get().getUserInterface().setStatusInfo(null);
			UIManager.get().getUserInterface().getStatusBar().setStatus(Status.DEFAULT);
		} else {
			Label label = new Label("The file contains " + documentErrors.size() + " error" + (documentErrors.size() == 1 ? "" : "s") + ", cannot be compiled.");
			label.setGraphic(UIManager.get().getAlertIcon(AlertType.WARNING, 16, 16));
			
			UIManager.get().getUserInterface().setStatusInfo(label);
			UIManager.get().getUserInterface().getStatusBar().setStatus(Status.ERROR);
		}
	}
	
	protected void onStreamParse() {
		
	}
	
	/**
	 * Replaces a split word of an ArgScriptLine. The line is fully contained in the specified DocumentFragment. 
	 * The document structure will be adapted accordingly.
	 * <p>
	 * The replacement is done in an no-event edit, meaning that this won't trigger any text events but it will apply
	 * syntax highlighting once done.
	 * @param fragment
	 * @param line
	 * @param newValue
	 * @param splitIndex
	 */
	public void replaceSplit(DocumentFragment fragment, ArgScriptLine line, String newValue, int splitIndex) {
		eventlessEdit(codeArea -> {
			replaceText(fragment, line.replaceSplit(fragment.getText(), newValue, splitIndex));
		});
	}
	
	@Override public void setDestinationFile(File file) {
		super.setDestinationFile(file);
		stream.setFolder(file.getParentFile());
	}
}
