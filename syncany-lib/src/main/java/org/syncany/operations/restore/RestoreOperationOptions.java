/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
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
 */
package org.syncany.operations.restore;

import java.util.Date;

import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.operations.OperationOptions;

public class RestoreOperationOptions implements OperationOptions {
	private FileHistoryId fileHistoryId;
	private Date databaseBeforeDate;
	private Integer fileVersionNumber;
	private String relativeTargetPath;

	public Date getDatabaseBeforeDate() {
		return databaseBeforeDate;
	}

	public void setDatabaseBeforeDate(Date databaseBeforeDate) {
		this.databaseBeforeDate = databaseBeforeDate;
	}

	public FileHistoryId getFileHistoryId() {
		return fileHistoryId;
	}

	public void setFileHistoryId(FileHistoryId fileHistory) {
		this.fileHistoryId = fileHistory;
	}

	public Integer getFileVersionNumber() {
		return fileVersionNumber;
	}

	public void setFileVersionNumber(Integer fileVersionNumber) {
		this.fileVersionNumber = fileVersionNumber;
	}

	public String getRelativeTargetPath() {
		return relativeTargetPath;
	}

	public void setRelativeTargetPath(String relativeTargetPath) {
		this.relativeTargetPath = relativeTargetPath;
	}
}