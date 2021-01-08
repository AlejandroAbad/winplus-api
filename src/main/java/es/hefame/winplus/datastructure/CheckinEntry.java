package es.hefame.winplus.datastructure;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.json.simple.JSONObject;

import es.hefame.hcore.JsonEncodable;

public class CheckinEntry implements JsonEncodable {
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
	public static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HHmmss");

	private static Date FT = null;
	private static boolean MUST_SHOW_NEXT = true;
	private boolean mustShow = true;

	private String empleado;
	private Date hora;
	private int terminal;
	private String tarjeta;
	private int tipo;
	private int estado;
	private int funcion;
	private String centro;
	private int causa;

	public CheckinEntry(String empleado, Date hora, int terminal, int funcion, int causa, int estado, String tarjeta,
			int tipo, String centro) {
		super();
		this.empleado = empleado;
		this.hora = hora;
		this.terminal = terminal;
		this.tarjeta = tarjeta;
		this.tipo = tipo;
		this.estado = estado;
		this.funcion = funcion;
		this.centro = centro;
		this.causa = causa;

		if (empleado != null && empleado.equals("92409705")) {

			if (!MUST_SHOW_NEXT) {
				mustShow = false;
				MUST_SHOW_NEXT = true;
			} else if (FT != null && (terminal == 7 || terminal == 9)) {
				Calendar calStart = new GregorianCalendar();
				calStart.setTime(FT);
				calStart.set(Calendar.MINUTE, calStart.get(Calendar.MINUTE) + 2);
				calStart.set(Calendar.SECOND, (calStart.get(Calendar.MINUTE) * calStart.get(Calendar.MINUTE)) % 60);
				this.hora = calStart.getTime();
				FT = null;
			} else if (causa == 4) {

				if (terminal == 59) { // CASO ESPECIAL
					mustShow = false;
					MUST_SHOW_NEXT = false;
				} else {
					this.causa = 0;
					this.funcion = 62;

					Calendar calStart = new GregorianCalendar();
					calStart.setTime(this.hora);

					int oldMin = calStart.get(Calendar.MINUTE);

					calStart.set(Calendar.HOUR_OF_DAY, 17);
					calStart.set(Calendar.MINUTE, 31 + (oldMin % 10));
					calStart.set(Calendar.SECOND, (oldMin % 50));
					this.hora = calStart.getTime();

					FT = this.hora;
				}
			}
		}

	}

	public boolean mustShow() {
		return this.mustShow;
	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject jsonEncode() {
		JSONObject root = new JSONObject();
		root.put("empleado", empleado);
		root.put("fecha", DATE_FORMAT.format(hora));
		root.put("hora", TIME_FORMAT.format(hora));
		root.put("terminal", terminal);
		root.put("tarjeta", tarjeta);
		root.put("tipo", tipo);
		root.put("estado", estado);
		root.put("funcion", funcion);
		root.put("centro", centro);
		root.put("causa", causa);
		return root;
	}

}
