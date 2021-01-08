package es.hefame.winplus.httpcontroller;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.hefame.hcore.HException;
import es.hefame.hcore.http.HttpException;
import es.hefame.hcore.http.HttpController;
import es.hefame.hcore.http.authentication.rfc7235.rfc7617.BasicAuthenticator;
import es.hefame.hcore.http.authentication.rfc7235.rfc7617.BasicPasswordMatcher;
import es.hefame.hcore.http.exchange.HttpConnection;
import es.hefame.hcore.http.exchange.IHttpRequest;
import es.hefame.winplus.datastructure.CheckinSet;
import es.hefame.winplus.datastructure.EmployeeList;

public class CheckinHandler extends HttpController
{
	private static Logger L = LogManager.getLogger();
	
	private static SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");

	public CheckinHandler()
	{
		this.setAuthenticator(new BasicAuthenticator("apiwinplus", new BasicPasswordMatcher()
		{
			@Override
			public boolean matchPassword(String realm, String username, String password, IHttpRequest request)
			{
				return "winplus".equals(password);
			}
		}));
	}

	@Override
	public void get(HttpConnection t) throws HException, IOException
	{
		this.put(t);
	}

	@Override
	public void put(HttpConnection t) throws HException, IOException
	{
		L.info("Petición de un conjunto de fichajes desde [{}]", () -> t.request.getIP());
		L.debug("Los parámetros de la petición son: {}", () -> t.request.getURIFields().toArray());

		String[] employee_list = null;

		String employee_code = t.request.getURIField(1);
		if (employee_code.equalsIgnoreCase("todos"))
		{
			employee_list = null;
		}
		else if (employee_code.equalsIgnoreCase("listado"))
		{
			String raw_json = t.request.getBodyAsString();
			EmployeeList list = new EmployeeList(raw_json);
			employee_list = list.to_array();
		}
		else
		{
			employee_list = new String[1];
			employee_list[0] = employee_code;
		}

		L.debug("Convirtiendo las fechas [{}, {}] a formato interno.", () -> t.request.getURIField(2), () -> t.request.getURIField(3));
		Date[] date_range = string_to_date(t.request.getURIField(2), t.request.getURIField(3));
		Date from_date = date_range[0];
		Date to_date = date_range[1];

		L.info("Filtros: [Empleados = {}, Inicio = {}, Fin = {}]", employee_list, from_date, to_date);

		CheckinSet.fetch_size = predict_result_number(from_date, to_date, ((employee_list == null) ? 800 : employee_list.length));

		CheckinSet checkin_set = new CheckinSet(employee_list, from_date, to_date);

		t.response.send(checkin_set, 200);
	}

	private Date[] string_to_date(String sdate, String edate) throws HttpException
	{
		Date[] result = new Date[2];
		result[0] = null;
		result[1] = null;

		Calendar now = Calendar.getInstance();
		
		if (sdate.equalsIgnoreCase("hoy"))
		{
			L.debug("Se ha especificado como fecha el valor especial [HOY]");
			sdate = formatter.format(new Date());
			edate = null;
		}

		if (sdate.equalsIgnoreCase("ayer"))
		{
			L.debug("Se ha especificado como fecha el valor especial [AYER]");
			now.add(Calendar.DATE, -1);
			sdate = formatter.format(now.getTime());
			edate = sdate;
		}

		result[0] = to_14_digits_date(sdate, true);
		result[1] = to_14_digits_date(edate, false);
		
		

		return result;
	}

	private Date to_14_digits_date(String date, boolean starting_day) throws HttpException
	{
		if (date == null) return null;

		Calendar now = Calendar.getInstance();

		if (date.length() == 6)
		{
			date = formatter.format(now.getTime()) + date;
		}
		else if (date.length() == 8)
		{
			if (starting_day)
			{
				date += "000000";
			}
			else
			{
				date += "235959";
			}
		}

		if (date.length() == 14)
		{
			try
			{
				int y = Integer.parseInt(date.substring(0, 4));
				int M = Integer.parseInt(date.substring(4, 6)) - 1;
				int d = Integer.parseInt(date.substring(6, 8));
				int h = Integer.parseInt(date.substring(8, 10));
				int m = Integer.parseInt(date.substring(10, 12));
				int s = Integer.parseInt(date.substring(12, 14));

				GregorianCalendar cal = new GregorianCalendar(y, M, d, h, m, s);
				return cal.getTime();
			}
			catch (NumberFormatException n)
			{
				L.error("Ocurrió una excepción al tratar la fecha [{}]", date);
				L.catching(n);
				throw new HttpException(400, "La fecha no es válida: " + n.getMessage());
			}
		}
		else
		{
			L.error("La fecha [{}] no es válida por no tener 14 caracteres.", date);
			throw new HttpException(400, "La fecha no es válida. El formato debe ser 'YYYYMMSSHHIISS'.");
		}
	}

	private int predict_result_number(Date from_date, Date to_date, long total_employees) throws HttpException
	{
		long days = (from_date != null && to_date != null) ? Math.max(1, (int) Math.abs((to_date.getTime() - from_date.getTime()) / 86400000)) : 10;
		long avg_checkins_per_day = 8;
		long estimated_checkins = Math.max(1, total_employees * days * avg_checkins_per_day);

		L.debug("La consulta es para un rango de [{}] día/s y [{}] empleados. Asumiendo [{}] fichajes por empleado y día el tamaño del buffer debería ser de [{}] registros", days, total_employees, avg_checkins_per_day, estimated_checkins);

		if (days > 365 && total_employees > 1) { throw new HttpException(400, "Para intervalos de tiempo superiores a un año, solo se permiten consultas de un único empleado a la vez."); }
		if (estimated_checkins > 50000) { throw new HttpException(400, "Se estima que la petición superará los 50000 fichajes. No se permiten realizar consultas tan grandes."); }

		return (int) Math.min(10000, estimated_checkins);
	}
}
