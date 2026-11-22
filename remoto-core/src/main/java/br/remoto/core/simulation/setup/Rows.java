package br.remoto.core.simulation.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * As linhas que um método {@code load*} do {@link ConfigurationAssembler} percorre: os parâmetros
 * tipados de uma tabela, projetados nas colunas do SELECT original, na mesma ordem.
 *
 * <p>Os getters seguem o JDBC: um nulo é lido como 0, false ou null.
 */
abstract class Rows
{
	abstract boolean next();
	abstract String getString(int column);
	abstract double getDouble(int column);
	abstract int getInt(int column);
	abstract boolean getBoolean(int column);
	abstract void close();

	/** Cada registro vira uma linha, com os valores na ordem das colunas do SELECT. */
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
