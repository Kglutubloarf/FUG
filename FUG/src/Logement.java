import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Random;

public class Logement {

	private static double TEMPERATURE_MAX = 25;
	private static double MIXED_NASH_PRECISION = 0.001;
	private static int PAS_COURBE_REDUCTION = 100;
	/**
	 * Si tous les usagers chauffe de EDF °C, on atteint la limite à partir de
	 * laquelle le gestionnaire doit payer plus pour le chauffage.
	 */
	private static double EDF = 3;
	private static double PLANCHER = 2;

	private int nombreUsagers;
	private int nombreStrategies;
	private Usager usagers[];
	private double temperatureUsager[];
	private int politique;
	private double courbeReduction[];

	public Logement(int nu, int nombreStrategies, double proportionEcolos, double proportionPollueurs,
			double proportionVoyageurs) {

		politique = 1;
		nombreUsagers = nu;
		this.nombreStrategies = nombreStrategies;
		double somme = proportionEcolos + proportionPollueurs + proportionVoyageurs;
		proportionEcolos /= somme;
		proportionVoyageurs /= somme;
		proportionPollueurs = 1 - proportionEcolos;

		usagers = new Usager[nu];
		int i = 0;
		for (; i < nu * proportionEcolos; i++)
			usagers[i] = new Usager(Usager.Type.ECOLO);
		for (; i < nu * (proportionEcolos + proportionVoyageurs); i++)
			usagers[i] = new Usager(Usager.Type.VOYAGEUR);
		for (; i < nu; i++)
			usagers[i] = new Usager(Usager.Type.POLLUEUR);

		for (int k = 0; k < nombreUsagers; k++)
			usagers[k].vecteurStochastique(nombreStrategies);
	}

	public void setPolitique(int pol) {
		politique = pol;
	}

	public double consommationIndividuelle(double temperature) {
		return temperature - Usager.TEMPERATURE_MINIMALE;
	}

	public void setTemperatureMoyenneUsagers() {
		temperatureUsager = new double[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			temperatureUsager[i] = 0;
			double[] tmp = usagers[i].mixedNash();
			for (int j = 0; j < nombreStrategies; j++)
				temperatureUsager[i] += strategieToTemperature(j, usagers[i]) * tmp[j] / usagers[i].mixedNashSum();
		}
	}

	public double consommationTotale() {
		double total = 0;
		for (double d : temperatureUsager) {
			total += consommationIndividuelle(d);
		}
		return total;
	}

	public double prixConsommationTotale(double consommationTotale) {
		double total = PLANCHER * nombreUsagers;

		if (consommationTotale < EDF * nombreUsagers)
			return total;

		return total + consommationTotale - EDF * nombreUsagers;
	}

	public double coutProprietaire() {

		double tout = prixConsommationTotale(consommationTotale());

		for (int i = 0; i < nombreUsagers; i++)
			tout += usagers[i].poidsPrixTransports() * temperatureToReduction(temperatureUsager[i]);

		return tout;
	}

	public double strategieToTemperature(int strat, Usager u) {
		return Usager.TEMPERATURE_MINIMALE
				+ strat * (u.temperatureIdeale() - Usager.TEMPERATURE_MINIMALE) / (nombreStrategies - 1);
	}

	public double temperatureToReduction(double temperature) {

		switch (politique) {
		case 0:
			return 0;
		case 1:
			return courbeReduction[(int) (PAS_COURBE_REDUCTION * (temperature - Usager.TEMPERATURE_MINIMALE)
					/ (TEMPERATURE_MAX - Usager.TEMPERATURE_MINIMALE))];
		default:
			return Math.exp(Usager.TEMPERATURE_MINIMALE - temperature);
		}
	}

	/**
	 * @param vecteurDeStrategies
	 *            un vecteur contenant un entier par usager qui indique la
	 *            stratégie choisie par cet usager.
	 * @param it
	 *            le nombre de vecteur de stratégies à tester.
	 * @return un vecteur de stratégie au même format que vecteurDeStrategies.
	 *         Celui-ci correspond à un équilibre de Nash pur. renvoie null si
	 *         aucun vecteur n'est trouvé au bout de it itérations.
	 */
	public int[] meilleureReponse(int[] vecteurDeStrategies, int it) throws Exception {

		if (it == 0)
			throw new Exception("Equilibre de Nash introuvable via un parcours");

		double facture = factureIndividuelle(vecteurDeStrategies);

		double utiliteMax;
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++)
			strategies[i] = vecteurDeStrategies[i];

		double utilite[] = new double[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			utilite[i] = usagers[i].utiliteTotale(strategieToTemperature(vecteurDeStrategies[i], usagers[i]), facture,
					temperatureToReduction(strategieToTemperature(vecteurDeStrategies[i], usagers[i])));

			utiliteMax = utilite[i];

			for (int k = 0; k < nombreStrategies; k++) {

				double tmp = usagers[i]
						.utiliteTotale(strategieToTemperature(k, usagers[i]),
								facture - (consommationIndividuelle(
										strategieToTemperature(vecteurDeStrategies[i], usagers[i])) / nombreUsagers)
								+ (consommationIndividuelle(strategieToTemperature(k, usagers[i])) / nombreUsagers),
						strategieToReduction(k, usagers[i]));

				if (utiliteMax < tmp) {
					strategies[i] = k;
					utiliteMax = tmp;
				}
			}
		}

		for (int i = 0; i < nombreUsagers; i++)
			if (strategies[i] != vecteurDeStrategies[i])
				return meilleureReponse(strategies, --it);

		return vecteurDeStrategies;

	}

	public int[] nashPur() {
		double utilite[][] = new double[(int) Math.pow(nombreStrategies, nombreUsagers)][nombreUsagers];

		int strategies[] = new int[nombreUsagers];
		int aiguille = 0;
		for (int j = 0; j < nombreUsagers; j++)
			strategies[j] = 0;

		// On calcule pour chaque joueur son utilité dans l'ensemble des
		// vecteurs de strategies
		for (int i = 0; i < (int) Math.pow(nombreStrategies, nombreUsagers); i++) {

			double facture = factureIndividuelle(strategies);

			for (int j = 0; j < nombreUsagers; j++)
				utilite[i][j] = usagers[j].utiliteTotale(strategieToTemperature(strategies[j], usagers[j]), facture,
						strategieToReduction(strategies[j], usagers[j]));

			strategies[aiguille]++;
			if (strategies[aiguille] == nombreStrategies) {
				while (strategies[aiguille] == nombreStrategies) {
					for (int k = 0; k <= aiguille; k++)
						strategies[k] = 0;
					aiguille++;
					if (aiguille == nombreUsagers)
						break;
					strategies[aiguille]++;
				}
				aiguille = 0;
			}
		}

		// Maintenant qu'on a tous les vecteurs d'utilité, on va voir si l'un
		// d'eux est un Nash pur.
		boolean nashPur;
		int stratj;
		for (int i = 0; i < (int) Math.pow(nombreStrategies, nombreUsagers); i++) {
			nashPur = true;
			for (int j = 0; j < nombreUsagers; j++) {
				int decalagej = (int) Math.pow(nombreStrategies, j);
				stratj = Math.floorMod(i, decalagej * nombreStrategies) / decalagej;

				for (int k = 0; k < nombreStrategies; k++)
					if (utilite[i][j] < utilite[i + (k - stratj) * decalagej][j])
						nashPur = false;
			}
			if (nashPur) {
				int[] strat = new int[nombreUsagers];
				for (int k = 0; k < nombreUsagers; k++)
					strat[k] = Math.floorMod(i, (int) Math.pow(nombreStrategies, k) * nombreStrategies)
							/ (int) Math.pow(nombreStrategies, k);
				return strat;
			}
		}
		return null;
	}

	/**
	 * @param vecteur
	 *            le vecteur de strategie des usagers
	 */
	private void setTemperatureUsagers(int[] vecteur) {
		temperatureUsager = new double[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			temperatureUsager[i] = strategieToTemperature(vecteur[i], usagers[i]);
		}
	}

	public double[] mixedNash(double b, double[] utilitePrecedente) {
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			double tmp = Math.random();
			strategies[i] = usagers[i].choisirStrategie(tmp);
		}

		double facture = factureIndividuelle(strategies);
		boolean stop = true;

		for (int i = 0; i < nombreUsagers; i++) {
			double utiliteTotale = usagers[i].utiliteTotale(strategieToTemperature(strategies[i], usagers[i]), facture,
					strategieToReduction(strategies[i], usagers[i]));
			usagers[i].updateStochastique(strategies[i], utiliteTotale, b, utilitePrecedente[i]);
			if (usagers[i].probabilite(strategies[i]) < 1 - MIXED_NASH_PRECISION)
				stop = false;
			utilitePrecedente[i] = utiliteTotale;
		}

		if (stop)
			return null;

		return utilitePrecedente;

	}

	public double strategieToReduction(int strat, Usager u) {
		return temperatureToReduction(strategieToTemperature(strat, u));
	}

	public double factureIndividuelle(int[] strategies) {

		double facture = 0;
		for (int j = 0; j < nombreUsagers; j++)
			facture += consommationIndividuelle(strategieToTemperature(strategies[j], usagers[j]));

		facture /= nombreUsagers;

		return facture;
	}

	public void afficherConsommation() {

		for (int i = 0; i < nombreUsagers; i++)
			System.out.println(usagers[i].type() + " température "
					+ (Usager.TEMPERATURE_MINIMALE + consommationIndividuelle(temperatureUsager[i])));
		System.out.println("consommation totale " + consommationTotale());

		System.out.println("cout propriétaire " + coutProprietaire());
		System.out.println();

	}

	public double analyse(int methode, int temperatureExterieure, boolean showResult) {

		double cmp;
		int pol;

		switch (methode) {
		case 1:
			nashPur();
			if (showResult)
				afficherConsommation();
			cmp = coutProprietaire();
			pol = politique;
			setPolitique(0);
			if (pol != 0) {
				analyse(1, temperatureExterieure, showResult);
				if (showResult)
					System.out.println("gain dû à la réduction " + (coutProprietaire() - cmp));
			}
			setPolitique(pol);
			return coutProprietaire() - cmp;

		default:
		case 2:
			int[] v = new int[nombreUsagers];
			for (int i = 0; i < nombreUsagers; i++)
				v[i] = 0;
			try {
				setTemperatureUsagers(meilleureReponse(v, 10000));
			} catch (Exception e) {
				e.printStackTrace();
				return -1;
			}
			if (showResult)
				afficherConsommation();
			cmp = coutProprietaire();

			pol = politique;
			setPolitique(0);
			if (pol != 0) {
				analyse(2, temperatureExterieure, showResult);
				if (showResult)
					System.out.println("gain dû à la réduction " + (coutProprietaire() - cmp));
			}
			setPolitique(pol);
			return coutProprietaire() - cmp;

		case 3:
			double b = 0.01;
			boolean nashFound = false;
			int testNash[] = new int[nombreUsagers];

			while (!nashFound) {
				double[] utilite = new double[nombreUsagers];
				for (int i = 0; i < nombreUsagers; i++)
					utilite[i] = 0;

				for (int i = 0; i < nombreUsagers; i++)
					usagers[i].vecteurStochastique(nombreStrategies);

				while (utilite != null) {
					utilite = mixedNash(b, utilite);
				}

				for (int i = 0; i < nombreUsagers; i++)
					testNash[i] = usagers[i].getPureFromStochastique(MIXED_NASH_PRECISION);

				nashFound = testMixedNash(testNash);
				b /= 2;
				if (b == 0) {
					MIXED_NASH_PRECISION /= 10;
					b = 0.01;
					System.err.println("FUG");
				}
			}
			setTemperatureMoyenneUsagers();
			if (showResult)
				afficherConsommation();
			cmp = coutProprietaire();
			pol = politique;
			setPolitique(0);
			if (pol != 0) {
				analyse(3, temperatureExterieure, showResult);
				if (showResult)
					System.out.println("gain dû à la réduction " + (coutProprietaire() - cmp));
			}
			setPolitique(pol);
			return coutProprietaire() - cmp;
		}
	}

	private boolean testMixedNash(int[] testNash) {
		double facture = factureIndividuelle(testNash);

		double utiliteMax;
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++)
			strategies[i] = testNash[i];

		double utilite[] = new double[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			utilite[i] = usagers[i].utiliteTotale(strategieToTemperature(testNash[i], usagers[i]), facture,
					temperatureToReduction(strategieToTemperature(testNash[i], usagers[i])));

			utiliteMax = utilite[i];

			for (int k = 0; k < nombreStrategies; k++) {

				double tmp = usagers[i].utiliteTotale(strategieToTemperature(k, usagers[i]),
						facture - (consommationIndividuelle(strategieToTemperature(testNash[i], usagers[i]))
								/ nombreUsagers)
						+ (consommationIndividuelle(strategieToTemperature(k, usagers[i])) / nombreUsagers),
						strategieToReduction(k, usagers[i]));

				if (utiliteMax < tmp) {
					strategies[i] = k;
					utiliteMax = tmp;
				}
			}
		}

		for (int i = 0; i < nombreUsagers; i++)
			if (strategies[i] != testNash[i])
				return false;

		return true;
	}

	/**
	 * @param max
	 *            f(0) = max.
	 * @param seed
	 *            la seed aléatoire générant la courbe.
	 * @return true si la courbe n'est pas affine
	 */
	public boolean setCourbeReduction(double max, long seed) {
		Random r = new Random(seed);

		courbeReduction = new double[PAS_COURBE_REDUCTION];
		for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
			courbeReduction[i] = 0;

		for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
			for (int k = r.nextInt(100); k >= 0; k--)
				courbeReduction[k] += 1 / (double) PAS_COURBE_REDUCTION * max;
		return true;
	}

	public static void main(String args[]) {
		Logement l = new Logement(30, 50, 50, 50, 50);
		l.setPolitique(1);
		double gainMax = Double.MIN_NORMAL;
		double gain;
		long seed;
		long bestSeed = -1;
		double bestAlpha = -1;
		Random rand = new java.util.Random();

		for (int k = 20; k <= 20; k++)
			for (int i = 0; i < 1; i++) {
				seed = rand.nextLong();
				if (l.setCourbeReduction(k / (double) 20, seed)) {
					gain = l.analyse(2, 10, false);
					if (gain > gainMax) {
						gainMax = gain;
						bestAlpha = k / (double) 20;
						bestSeed = seed;
					}
				} else
					i--;
			}

		l.setCourbeReduction(bestAlpha, bestSeed);
		PrintWriter pw;
		try {
			pw = new PrintWriter("C:\\Users\\Moi\\git\\FUG\\FUG\\bin\\a.txt");
			for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
				pw.println(Usager.TEMPERATURE_MINIMALE
						+ (TEMPERATURE_MAX - Usager.TEMPERATURE_MINIMALE) * i / PAS_COURBE_REDUCTION + " "
						+ l.courbeReduction[i]);
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		System.out.println("Alpha " + bestAlpha + ", Seed " + bestSeed + ", gain " + gainMax);
		l.setPolitique(1);
		l.analyse(2, 10, true);
	}
}
