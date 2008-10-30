package org.objectstyle.wolips.eomodeler.core.sql;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSMutableSet;
import com.webobjects.foundation.NSSet;

public class EOFSQLUtils {

	public static Object toWOCollections(Object obj) {
		Object result;
		if (obj instanceof Map) {
			NSMutableDictionary nsDict = new NSMutableDictionary();
			Map map = (Map) obj;
			Iterator entriesIter = map.entrySet().iterator();
			while (entriesIter.hasNext()) {
				Map.Entry entry = (Map.Entry) entriesIter.next();
				Object key = entry.getKey();
				Object value = entry.getValue();
				if (key != null && value != null) {
					key = toWOCollections(key);
					value = toWOCollections(value);
					nsDict.setObjectForKey(value, key);
				}
			}
			result = nsDict;
		} else if (obj instanceof List) {
			NSMutableArray nsArray = new NSMutableArray();
			List list = (List) obj;
			Iterator valuesEnum = list.iterator();
			while (valuesEnum.hasNext()) {
				Object value = valuesEnum.next();
				if (value != null) {
					value = toWOCollections(value);
					nsArray.addObject(value);
				}
			}
			result = nsArray;
		} else if (obj instanceof Set) {
			Set set = (Set) obj;
			NSMutableSet nsSet = new NSMutableSet();
			Iterator valuesEnum = set.iterator();
			while (valuesEnum.hasNext()) {
				Object value = valuesEnum.next();
				if (value != null) {
					value = toWOCollections(value);
					nsSet.addObject(value);
				}
			}
			result = nsSet;
		} else {
			result = obj;
		}
		return result;
	}

	public static Object toJavaCollections(Object obj) {
		Object result;
		if (obj instanceof NSDictionary) {
			Map map = new HashMap();
			NSDictionary nsDict = (NSDictionary) obj;
			Enumeration keysEnum = nsDict.allKeys().objectEnumerator();
			while (keysEnum.hasMoreElements()) {
				Object key = keysEnum.nextElement();
				Object value = nsDict.objectForKey(key);
				key = toJavaCollections(key);
				value = toJavaCollections(value);
				map.put(key, value);
			}
			result = map;
		} else if (obj instanceof NSArray) {
			List list = new LinkedList();
			NSArray nsArray = (NSArray) obj;
			Enumeration valuesEnum = nsArray.objectEnumerator();
			while (valuesEnum.hasMoreElements()) {
				Object value = valuesEnum.nextElement();
				value = toJavaCollections(value);
				list.add(value);
			}
			result = list;
		} else if (obj instanceof NSSet) {
			Set set = new HashSet();
			NSSet nsSet = (NSSet) obj;
			Enumeration valuesEnum = nsSet.objectEnumerator();
			while (valuesEnum.hasMoreElements()) {
				Object value = valuesEnum.nextElement();
				value = toJavaCollections(value);
				set.add(value);
			}
			result = set;
		} else {
			result = obj;
		}
		return result;
	}
	
	/**
	 * Splits semicolon-separate sql statements into an array of strings
	 * 
	 * @param sql
	 *            a multi-line sql statement
	 * @return an array of sql statements
	 */
	public static List<String> splitSQLStatements(String sql, char commandSeparatorChar) {
		List<String> statements = new LinkedList<String>();
		if (sql != null) {
			StringBuffer statementBuffer = new StringBuffer();
			int length = sql.length();
			boolean inQuotes = false;
			for (int i = 0; i < length; i++) {
				char ch = sql.charAt(i);
				if (ch == '\r' || ch == '\n') {
					// ignore
				}
				else if (!inQuotes && ch == commandSeparatorChar) {
					String statement = statementBuffer.toString().trim();
					if (statement.length() > 0) {
						statements.add(statement);
					}
					statementBuffer.setLength(0);
				}
				else {
					// Support for escaping apostrophes, e.g. 'Mike\'s Code' 
					if (inQuotes && ch == '\\') {
						statementBuffer.append(ch);
						ch = sql.charAt(++ i);
					}
					else if (ch == '\'') {
						inQuotes = !inQuotes;
					}
					statementBuffer.append(ch);
				}
			}
			String statement = statementBuffer.toString().trim();
			if (statement.length() > 0) {
				statements.add(statement);
			}
		}
		return statements;
	}

}
