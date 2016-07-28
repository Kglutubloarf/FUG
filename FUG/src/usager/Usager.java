package usager;

public abstract class Usager {

	/**
	 * La température minimale supportée par l'usager.
	 */
	public static final double TEMPERATURE_MINIMALE = 15;

	/**
	 * Plus cette valeur est élevée, plus la dérivée de la fonction de calcul
	 * d'utilité de la réduction de coût de transport décroît rapidement.
	 */
	public static final double MULTIPLICATEUR_REDUCTION = 5;

	/**
	 * Plus cette valeur est élevée, moins les usagers ressentent un écart à
	 * leur température idéale.
	 */
	public static final double TOLERANCE_THERMIQUE = 0.05;

	/**
	 * Plus richesse est élevée, moins les usagers sont dérangés par un prix du
	 * chauffage élevé. leur utilité vaut 1 lorsque le chauffage est gratuit, et
	 * vaut 0.368 lorsque leur facture s'élève à "richesse".
	 */
	public static final double RICHESSE = 1;

	/**
	 * La température à laquelle l'usager souhaiterait chauffer son logement.
	 */
	private double temperatureIdeale;

	/**
	 * L'importance du prix du chauffage vis-à-vis du prix des transports en
	 * commun et du confort.
	 */
	private double poidsPrixChauffage;

	/**
	 * L'importance du prix des transports en commun du vis-à-vis du prix du
	 * chauffage et du confort.
	 */
	private double poidsPrixTransports;

	/**
	 * L'importance du confort vis-à-vis du prix des transports en commun et du
	 * prix du chauffage.
	 */
	private double poidsConfort;

	/**
	 * Vecteur stochastique attribuant une probabilité à chaque stratégie.
	 */
	private double vecteurStochastique[];

	/**
	 * Le nombre de stratégies que l'usager peut suivre.
	 */
	private int nombreStrategies;

	protected Usager(double temperatureIdeale, double poidsPrixChauffage, double poidsPrixTransports,
			double poidsConfort) {

		this.temperatureIdeale = temperatureIdeale;

		double somme = poidsPrixChauffage + poidsPrixTransports + poidsConfort;
		this.poidsPrixChauffage = poidsPrixChauffage / somme;
		this.poidsPrixTransports = poidsPrixTransports / somme;
		this.poidsConfort = 1 - this.poidsPrixChauffage - this.poidsPrixTransports;
	}

	/**
	 * Initialise un vecteur stochastique {@link #vecteurStochastique} propre à
	 * l'usager en attribuant à chaque élément la même valeur.
	 * 
	 * @param nombreDeStrategies
	 *            la taille du vecteur à initialiser.
	 */
	public void setVecteurStochastique(int nombreDeStrategies) {
		vecteurStochastique = new double[nombreDeStrategies];
		for (int i = 0; i < nombreDeStrategies; i++)
			vecteurStochastique[i] = (double) 1 / nombreDeStrategies;

		this.nombreStrategies = nombreDeStrategies;
	}

	/**
	 * Choisit une stratégie en fonction du vecteur stochastique initialisé par
	 * {@link #setVecteurStochastique(int)} et modifier par
	 * {@link #updateStochastique(int, double, double, double)}
	 * 
	 * @param alea
	 *            un nombre aléatoire entre 0 et 1.
	 * @return la stratégie choisie.
	 */
	public int choisirStrategie(double alea) {

		double tmp = 0;
		for (int i = 0; i < nombreStrategies; i++) {
			if ((tmp + vecteurStochastique[i]) > alea)
				return i;
			tmp += vecteurStochastique[i];
		}

		return nombreStrategies - 1;
	}

	public double utiliteTotale(double temperature, double facture, double reduction) {
		double satisfaction = 0;
		satisfaction += utiliteTemperature(temperature) * poidsConfort;
		satisfaction += utilitePrixChauffage(facture) * poidsPrixChauffage;
		satisfaction += utiliteReductionTransports(reduction) * poidsPrixTransports;
		return satisfaction;
	}

	private double utiliteReductionTransports(double reduction) {
		return (1 - Math.exp(-reduction * MULTIPLICATEUR_REDUCTION)) / (1 - Math.exp(-MULTIPLICATEUR_REDUCTION));
	}

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

	private double utiliteTemperature(double temperature) {
		temperature = normaliseTemperature(temperature);
		double ti = normaliseTemperature(temperatureIdeale);

		return Math.exp(-(Math.pow(ti - temperature, 2) / TOLERANCE_THERMIQUE));
	}

	/**
	 * @param strat
	 *            la stratégie utilisée dernièrement.
	 * @param utiliteTotale
	 *            l'utilité dégagée par cette stratégie.
	 * @param b
	 *            cf. Linear Reward Inaction. 0 < b < 1
	 * @param utilitePrecedente
	 *            l'utilité obtenue grâce à la stratégie précédente.
	 */
	public double updateStochastique(int strat, double utiliteTotale, double b, double utilitePrecedente) {

		if (utiliteTotale < utilitePrecedente)
			return vecteurStochastique[strat];

		double gain = utiliteTotale * b;
		for (int i = 0; i < nombreStrategies; i++)
			if (i != strat) {
				vecteurStochastique[i] -= gain * vecteurStochastique[i];
			}
		vecteurStochastique[strat] += gain * (1 - vecteurStochastique[strat]);

		return vecteurStochastique[strat];
	}

	public double[] vecteurStochastique() {
		return vecteurStochastique;
	}

	public double poidsPrixTransports() {
		return poidsPrixTransports / (poidsConfort + poidsPrixChauffage + poidsPrixTransports);
	}

	public double temperatureIdeale() {
		return temperatureIdeale;
	}

	/**
	 * @param limite
	 *            une stratégie est considérée comme pouvant appartenir à un
	 *            Nash pur si sa probabilité d'être tirée est proche (<limite)
	 *            de 1.
	 * @return l'unique stratégie dont la probabilité approche 1. -1 si aucune
	 *         stratégie ne correspond.
	 */
	public int getPureFromStochastique(double limite) {
		for (int i = 0; i < nombreStrategies; i++)
			if (vecteurStochastique[i] > 1 - limite)
				return i;

		new Exception().printStackTrace();
		return -1;
	}

	public double probabilite(int i) {
		return vecteurStochastique[i];
	}
}