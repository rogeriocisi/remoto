package br.remoto.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The rows a loader of ConfigurationDAO reads: either a JDBC result set or the typed parameters
 * of br.remoto.core.model.config, projected to the columns of the loader's SELECT.
 *
 * <p>The getters follow JDBC: a null reads as 0, false or null.
 */
abstract class Rows
{
	abstract boolean next() throws SQLException;
	abstract String getString(int column) throws SQLException;
	abstract double getDouble(int column) throws SQLException;
	abstract int getInt(int column) throws SQLException;
	abstract boolean getBoolean(int column) throws SQLException;
	abstract void close() throws SQLException;


	static Rows of(Statement statement, ResultSet rs)
	{
		return new Rows()
		{
			boolean next() throws SQLException { return rs.next(); }
			String getString(int c) throws SQLException { return rs.getString(c); }
			double getDouble(int c) throws SQLException { return rs.getDouble(c); }
			int getInt(int c) throws SQLException { return rs.getInt(c); }
			boolean getBoolean(int c) throws SQLException { return rs.getBoolean(c); }
			void close() throws SQLException { rs.close(); statement.close(); }
		};
	}


	/** Each record becomes one row, its values in the order of the SELECT columns. */
	static <T> Rows of(List<T> records, Function<T, Object[]> columns)
	{
		List<Object[]> rows = new ArrayList<>(records.size());
		for( T r : records )
			rows.add(columns.apply(r));

		return new Rows()
		{
			private int i = -1;

			boolean next() { return ++i < rows.size(); }
			String getString(int c) { return (String)value(c); }
			double getDouble(int c) { Object v = value(c); return v == null ? 0 : ((Number)v).doubleValue(); }
			int getInt(int c) { Object v = value(c); return v == null ? 0 : ((Number)v).intValue(); }
			boolean getBoolean(int c)
			{
				Object v = value(c);
				if( v instanceof Boolean b ) return b;
				return v != null && ((Number)v).intValue() != 0;
			}
			void close() { }

			private Object value(int c) { return rows.get(i)[c - 1]; }
		};
	}
}
