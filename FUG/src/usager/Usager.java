package usager;

public abstract class Usager {

	/**
	 * La temp�rature minimale support�e par l'usager.
	 */
	public static final double TEMPERATURE_MINIMALE = 15;

	/**
	 * Plus cette valeur est �lev�e, plus la d�riv�e de la fonction de calcul
	 * d'utilit� de la r�duction de co�t de transport d�cro�t rapidement.
	 */
	public static final double MULTIPLICATEUR_REDUCTION = 5;

	/**
	 * Plus cette valeur est �lev�e, moins les usagers ressentent un �cart �
	 * leur temp�rature id�ale.
	 */
	public static final double TOLERANCE_THERMIQUE = 0.05;

	/**
	 * Plus richesse est �lev�e, moins les usagers sont d�rang�s par un prix du
	 * chauffage �lev�. leur utilit� vaut 1 lorsque le chauffage est gratuit, et
	 * vaut 0.368 lorsque leur facture s'�l�ve � "richesse".
	 */
	public static final double RICHESSE = 1;

	/**
	 * La temp�rature � laquelle l'usager souhaiterait chauffer son logement.
	 */
	private double temperatureIdeale;

	/**
	 * L'importance du prix du chauffage vis-�-vis du prix des transports en
	 * commun et du confort.
	 */
	private double poidsPrixChauffage;

	/**
	 * L'importance du prix des transports en commun du vis-�-vis du prix du
	 * chauffage et du confort.
	 */
	private double poidsPrixTransports;

	/**
	 * L'importance du confort vis-�-vis du prix des transports en commun et du
	 * prix du chauffage.
	 */
	private double poidsConfort;

	/**
	 * Vecteur stochastique attribuant une probabilit� � chaque strat�gie.
	 */
	private double vecteurStochastique[];

	/**
	 * Le nombre de strat�gies que l'usager peut suivre.
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
	 * Initialise un vecteur stochastique {@link #vecteurStochastique} propre �
	 * l'usager en attribuant � chaque �l�ment la m�me valeur.
	 * 
	 * @param nombreDeStrategies
	 *            la taille du vecteur � initialiser.
	 */
	public void setVecteurStochastique(int nombreDeStrategies) {
		vecteurStochastique = new double[nombreDeStrategies];
		for (int i = 0; i < nombreDeStrategies; i++)
			vecteurStochastique[i] = (double) 1 / nombreDeStrategies;

		this.nombreStrategies = nombreDeStrategies;
	}

	/**
	 * Choisit une strat�gie en fonction du vecteur stochastique initialis� par
	 * {@link #setVecteurStochastique(int)} et modifier par
	 * {@link #updateStochastique(int, double, double, double)}
	 * 
	 * @param alea
	 *            un nombre al�atoire entre 0 et 1.
	 * @return la strat�gie choisie.
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
	 * renvoie une temp�rature normalis�e entre 0 et 1. 0 = temp�rature minimale
	 * 1 = temp�rature id�ale
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
	 *            la strat�gie utilis�e derni�rement.
	 * @param utiliteTotale
	 *            l'utilit� d�gag�e par cette strat�gie.
	 * @param b
	 *            cf. Linear Reward Inaction. 0 < b < 1
	 * @param utilitePrecedente
	 *            l'utilit� obtenue gr�ce � la strat�gie pr�c�dente.
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
	 *            une strat�gie est consid�r�e comme pouvant appartenir � un
	 *            Nash pur si sa probabilit� d'�tre tir�e est proche (<limite)
	 *            de 1.
	 * @return l'unique strat�gie dont la probabilit� approche 1. -1 si aucune
	 *         strat�gie ne correspond.
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