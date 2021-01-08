package es.hefame.winplus.datastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.json.simple.JSONArray;

import es.hefame.hcore.JsonEncodable;
import es.hefame.hcore.HException;
import es.hefame.hcore.http.HttpException;
import es.hefame.hcore.oracle.DBConnection;
import es.hefame.hcore.oracle.OracleException;

public class CheckinSet implements JsonEncodable
{
	private static final Marker	PLSQL		= MarkerManager.getMarker("PLSQL");
	public static int			fetch_size	= 50;
	private static Logger		L			= LogManager.getLogger();
	private List<CheckinEntry>	lines;

	public CheckinSet(String[] cod_empleado, Date from_date, Date to_date) throws HException
	{
		PreparedStatement preparedStatement = null;
		ResultSet rs = null;

		lines = new LinkedList<CheckinEntry>();

		if (cod_empleado != null && cod_empleado.length == 0)
		{
			L.info("Se solicita un conjunto de empleados vacio (pero no nulo). Se devuelve un conjunto de fichajes vacio.");
			return;
		}

		try
		{
			preparedStatement = generate_sql(cod_empleado, from_date, to_date);

			rs = preparedStatement.executeQuery();
			rs.setFetchSize(fetch_size);

			L.debug("Leyendo resultado de la BBDD");
			int n_o_lines = 0;
			while (rs.next())
			{
				try
				{
					String empleado = rs.getString(1);
					Date hora = rs.getDate(2);
					int terminal = rs.getInt(3);
					int funcion = rs.getInt(4);
					int causa = rs.getInt(5);
					int estado = rs.getInt(6);
					String tarjeta = rs.getString(7);
					int tipo = rs.getInt(8);
					String centro = rs.getString(9);
					
					L.trace("Leido registro [{}, {}, {}, {}, {}, {}, {}, {}, {}]", empleado, hora, terminal, funcion,causa, estado,tarjeta,tipo, centro);

					CheckinEntry entry = new CheckinEntry(empleado, hora, terminal, funcion, causa, estado, tarjeta, tipo, centro);
					
					if (entry.mustShow()) {
						this.lines.add(entry);
						n_o_lines++;
					}
				}
				catch (SQLException e)
				{
					L.catching(e);
				}
			}
			L.info("Se han obtenido [{}] líneas", n_o_lines);

		}
		catch (Exception e)
		{
			L.error("Ha ocurrido una excepción [{}] mientras se realizaba la consulta SQL", e.getClass().getName());
			L.catching(e);
			if (e instanceof SQLException)
			{
				throw new OracleException((SQLException)e);
			}
			else
			{
				throw new HttpException(500, "Ha ocurrido una excepción mientras se realizaba la consulta", e);
			}
		}
		finally
		{
			DBConnection.clearResources(preparedStatement, rs);
		}

	}

	private PreparedStatement generate_sql(String[] cod_empleado, Date from_date, Date to_date) throws HException, SQLException
	{
		Map<String, Object[]> filters = new HashMap<String, Object[]>();
		if (cod_empleado != null)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("(PERSONAL IN (");

			for (int i = 0; i < cod_empleado.length; i++)
			{
				if (i > 0)
				{
					if (((i % 1000) == 0) && (i != cod_empleado.length - 1))
					{
						sb.append(") OR PERSONAL IN (");
					}
					else
					{
						sb.append(',');
					}
				}
				sb.append('?');
			}
			sb.append("))");
			filters.put(sb.toString(), cod_empleado);
		}

		if (from_date != null)
		{
			Date[] list = new Date[1];
			list[0] = from_date;
			filters.put("HORA >= ?", list);
		}

		if (to_date != null)
		{
			Date[] list = new Date[1];
			list[0] = to_date;
			filters.put("HORA <= ?", list);
		}

		String select1 = "SELECT PERSONAL, HORA, TERMINAL, FUNCION, 0 AS CAUSA, ESTADO, TARJETA, TIPO, CENTRO FROM WINPLUS.FICHAJESACCESOS";
		String select2 = "SELECT PERSONAL, HORA, TERMINAL, FUNCION, CAUSA, ESTADO, TARJETA, 0 AS TIPO, CENTRO FROM WINPLUS.FICHAJES";

		String order_clause = " ORDER BY PERSONAL, HORA";
		StringBuilder where_clause = new StringBuilder();

		if (filters.size() > 0)
		{
			where_clause.append(" WHERE ");
			boolean first = true;
			for (Entry<String, Object[]> e : filters.entrySet())
			{
				if (!first)
				{
					where_clause.append(" AND ");
				}
				first = false;
				where_clause.append(e.getKey());
			}
		}

		StringBuilder query = new StringBuilder();
		query.append(select1).append(where_clause.toString()).append(" UNION ALL ").append(select2).append(where_clause.toString()).append(order_clause);

		PreparedStatement stmt = null;
		Connection dbConnection = DBConnection.get();

		String preparing_query = query.toString();
		L.debug(PLSQL, "Preparing query: [{}]", preparing_query);
		stmt = dbConnection.prepareStatement(preparing_query);

		if (filters.size() > 0)
		{
			int idx = 1;
			int no_filters = 0;

			for (Entry<String, Object[]> e : filters.entrySet())
			{
				Object[] o = e.getValue();
				no_filters += o.length;
			}

			for (Entry<String, Object[]> e : filters.entrySet())
			{
				Object[] o = e.getValue();
				if (o instanceof String[])
				{
					String[] values = (String[]) o;
					for (String value : values)
					{
						stmt.setString(idx, value);
						stmt.setString(idx + no_filters, value);
						L.trace(PLSQL, "Parameters {} and {} set to {} ({})", idx, idx + no_filters, value, value.getClass());
						idx++;
					}
				}

				if (o instanceof Date[])
				{
					Date[] values = (Date[]) o;

					for (Date value : values)
					{
						java.sql.Date sql_date = new java.sql.Date(((Date) value).getTime());
						stmt.setDate(idx, sql_date);
						stmt.setDate(idx + no_filters, sql_date);
						L.trace(PLSQL, "Parameters {} and {} set to {} ({})", idx, idx + no_filters, sql_date, sql_date.getClass());
						idx++;
					}
				}
			}
		}

		return stmt;

	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONArray jsonEncode()
	{
		JSONArray list = new JSONArray();
		for (CheckinEntry entry : this.lines)
		{
			list.add(entry.jsonEncode());
		}
		return list;
	}
}
