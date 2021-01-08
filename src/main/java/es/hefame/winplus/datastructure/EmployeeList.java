package es.hefame.winplus.datastructure;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import es.hefame.hcore.http.HttpException;

public class EmployeeList
{
	private static Logger	L				= LogManager.getLogger();
	private List<String>	employee_codes	= new LinkedList<String>();

	@SuppressWarnings("unchecked")
	public EmployeeList(String json_raw) throws HttpException
	{
		JSONParser parser = new JSONParser();

		try
		{

			L.debug("Analizando la lista de empleados proporcionada: {}", json_raw);

			Object obj = parser.parse(json_raw);
			JSONArray jsonObject = (JSONArray) obj;

			Iterator<Object> it = jsonObject.iterator();

			while (it.hasNext())
			{
				JSONObject json_obj = (JSONObject) it.next();
				Object employee_code_generic = json_obj.get("EMPLEADO");

				if (employee_code_generic == null)
				{
					employee_code_generic = json_obj.get("PERNR");
					if (employee_code_generic == null) { throw L.throwing(new HttpException(400, "El JSON es incorrecto. Cada empleado debe tener el campo 'EMPLEADO' o 'PERNR'.")); }
				}

				if (employee_code_generic instanceof Long)
				{
					employee_codes.add(sanitize_employee_code((Long) employee_code_generic));
				}
				else if (employee_code_generic instanceof String)
				{
					employee_codes.add(sanitize_employee_code((String) employee_code_generic));
				}
				else
				{
					L.error("El código del empleado de tipo [{}] no es válido", employee_code_generic.getClass());
					throw L.throwing(new HttpException(400, "El JSON es incorrecto. El tipo de datos del campo 'EMPLEADO' no está soportado."));
				}
			}

			L.debug("La lista de empleados es {}", this.employee_codes);
		}
		catch (ParseException | ClassCastException e)
		{
			L.error("Ocurrió una excepción al analizar el JSON de entrada.");
			L.catching(e);
			throw new HttpException(400, "No se entiende el JSON enviado. Se esperaba un array JSON.", e);
		}
	}

	public String[] to_array()
	{
		return this.employee_codes.toArray(new String[0]);
	}

	private String sanitize_employee_code(long code)
	{
		return sanitize_employee_code(String.valueOf(code));
	}

	private String sanitize_employee_code(String code)
	{
		StringBuffer padded = new StringBuffer();

		for (int i = 0; i < (8 - code.length()); i++)
		{
			padded.append('0');
		}

		padded.append(code);
		return padded.toString();
	}

}
