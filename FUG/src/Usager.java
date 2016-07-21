import java.math.RoundingMode;
import java.text.DecimalFormat;

public class Usager {

	public enum Type {
		ECOLO, POLLUEUR, INDEFINI, PERSONNALISE, VOYAGEUR
	}

	public static final double TEMPERATURE_MINIMALE = 15;
	public static final double MULTIPLICATEUR_REDUCTION = 5;
	public static final double TOLERANCE_THERMIQUE = 0.05;
	public static final double RICHESSE = 1;

	private Type type;
	private double temperatureIdeale;
	private double poidsPrixChauffage;
	private double poidsPrixTransports;
	private double poidsConfort;

	private double mixedNash[];
	private double mixedNashSum;
	private int nombreStrategies;

	public Usager(double ti, double ppc, double ppt, double pc) {
		type = Type.PERSONNALISE;
		definirUsager(ti, ppc, ppt, pc);
	}

	private void definirUsager(double temperatureIdeale, double poidsPrixChauffage, double poidsPrixTransports,
			double poidsConfort) {

		this.temperatureIdeale = temperatureIdeale;

		double somme = poidsPrixChauffage + poidsPrixTransports + poidsConfort;
		this.poidsPrixChauffage = poidsPrixChauffage / somme;
		this.poidsPrixTransports = poidsPrixTransports / somme;
		this.poidsConfort = 1 - this.poidsPrixChauffage - this.poidsPrixTransports;
	}

	public void vecteurStochastique(int nombreDeStrategies) {
		mixedNash = new double[nombreDeStrategies];
		for (int i = 0; i < nombreDeStrategies; i++)
			mixedNash[i] = (double) 1 / nombreDeStrategies;

		mixedNashSum = 1;
		this.nombreStrategies = nombreDeStrategies;
	}

	public int choisirStrategie(double proba) {

		double tmp = 0;
		for (int i = 0; i < nombreStrategies; i++) {
			if ((tmp + mixedNash[i]) / mixedNashSum > proba)
				return i;
			tmp += mixedNash[i];
		}

		return nombreStrategies - 1;
	}

	@SuppressWarnings("unused")
	private static double arrondiTemperature(double valeur) {
		DecimalFormat df = new DecimalFormat("#.##");
		df.setRoundingMode(RoundingMode.HALF_DOWN);
		return Double.parseDouble(df.format(valeur));
	}

	public Usager(Type type) {
		setType(type);
	}

	public void setType(Type t) {
		type = t;
		switch (t) {
		case ECOLO:
			definirUsager(17, 1, 0, 0);
			break;
		case POLLUEUR:
			definirUsager(22, 0, 0, 1);
			break;
		case VOYAGEUR:
			definirUsager(22, 0, 1, 0);
			break;
		default:
			type = Type.INDEFINI;
			break;
		}
	}

	public double utiliteTotale(double temperature, double facture, double reduction) {
		double satisfaction = 0;
		satisfaction += utiliteTemperature(temperature) * poidsConfort;
		satisfaction += utilitePrixChauffage(facture) * poidsPrixChauffage;
		satisfaction += utiliteReductionTransports(reduction) * poidsPrixTransports;
		return satisfaction;
	}

	/**
	 * La courbe d'utilité est une courbe exponentielle inversé avec f(0) = 0 et
	 * f(1) = 1.
	 */
	private double utiliteReductionTransports(double reduction) {
		return (1 - Math.exp(-reduction * MULTIPLICATEUR_REDUCTION)) / (1 - Math.exp(-MULTIPLICATEUR_REDUCTION));
	}

	/**
	 * La courbe d'utilité est une courbe exponentielle inversé avec f(0) = 1 et
	 * f(+infini) = 0.
	 */
	private double utilitePrixChauffage(double facture) {
		return Math.exp(-facture / RICHESSE);
	}

	/**
	 * renvoie une température normalisée entre 0 et 1. 0 = température minimale
	 * 1 = température idéale
	 */
	private double normaliseTemperature(double temp) {
		return (temp - TEMPERATURE_MINIMALE) / (temperatureIdeale - TEMPERATURE_MINIMALE);
	}

	/**
	 * La courbe d'utilité est une courbe de Gauss.
	 */
	private double utiliteTemperature(double temperature) {
		temperature = normaliseTemperature(temperature);
		double ti = normaliseTemperature(temperatureIdeale);

		return Math.exp(-(Math.pow(ti - temperature, 2) / TOLERANCE_THERMIQUE));
	}

	@Override
	public String toString() {
		String res = new String(type.toString());
		res = res.concat("\nutilité confort : " + poidsConfort);
		res = res.concat("\nutilité mobilité : " + poidsPrixTransports);
		res = res.concat("\nutilité facture chauffage : " + poidsPrixChauffage);
		return res;

	}

	public Type type() {
		return type;
	}

	/**
	 * 
	 * @param strat
	 *            la stratégie qui a été utilisée en dernier
	 * @param utiliteTotale
	 *            l'utilité qu'a dégagée cette stratégie
	 * @return la différence entre l'ancienne valeur contenue dans le vecteur
	 *         stochastique et la nouvelle.
	 */
	public double updateStochastique(int strat, double utiliteTotale) {

		double multiplicateur = 2;

		mixedNash[strat] += multiplicateur * utiliteTotale / nombreStrategies;
		mixedNashSum += multiplicateur * utiliteTotale / nombreStrategies;
		return utiliteTotale;
	}

	public double mixedNashSum() {
		return mixedNashSum;
	}

	public double[] mixedNash() {
		return mixedNash;
	}

	public double poidsPrixTransports() {
		return poidsPrixTransports / (poidsConfort + poidsPrixChauffage + poidsPrixTransports);
	}

	public double temperatureIdeale() {
		return temperatureIdeale;
	}

}