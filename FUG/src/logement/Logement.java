package logement;

import java.awt.Color;
import java.awt.Graphics;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Random;

import usager.Ecolo;
import usager.Pollueur;
import usager.Usager;
import usager.Voyageur;

public class Logement {

	/**
	 * {@link #politique}
	 */
	public enum Politique {
		/**
		 * Les usagers n'ont jamais de réduction.
		 */
		AUCUNEREDUCTION,
		/**
		 * La fonction de réduction optimale pour le gestionnaire est calculée
		 * suivant un algorithme de Monte-Carlo.
		 */
		MONTECARLO,
		/**
		 * La fonction de réduction est une exponentielle inversée.
		 */
		TEST
	}

	/**
	 * {@link #methode}
	 */
	public enum Methode {
		/**
		 * On calcule toutes les combinaisons de stratégies et on trouve parmi
		 * elles un équilibre de Nash pur. très lent.
		 */
		BRUTEFORCE,
		/**
		 * Les usagers choisissent une stratégie, calcule leur utilité, observe
		 * la situation, et choisissent à l'itération suivante la stratégie qui
		 * aurait donné la meilleure utilité à cette itération. On s'arrête
		 * lorsqu'on trouve un Nash pur. Bonnes performances.
		 */
		MEILLEUREREPONSE,
		/**
		 * Linear Reward Inaction cf. internet. Lent mais améliorable.
		 */
		LRI
	}

	/**
	 * La politique que suit le gestionnaire pour attribuer des réductions aux
	 * usagers, parmi : {@link #AUCUNEREDUCTION}, {@link #MONTECARLO},
	 * {@link #TEST}.
	 */
	private Politique politique;

	/**
	 * La méthode choisie pour trouver un équilibre de Nash dans le jeu entre
	 * les usagers. Parmi : {@link #BRUTEFORCE}, {@link #MEILLEUREREPONSE},
	 * {@link #LRI}.
	 */
	private Methode methode;

	/**
	 * La température maximale à laquelle les usagers peuvent chauffer leur
	 * logement.
	 */
	private static final double TEMPERATURE_MAX = 25;
	/**
	 * dans un algorithme LRI, si pour chaque usager, il existe une stratégie de
	 * probabilité proche de 1 (distance < LRI_PRECISION), on teste s'il s'agit
	 * d'un Nash pur potentiel.
	 */
	private static double LRI_PRECISION = 0.001;
	/**
	 * Lorsqu'on cherche la meilleure fonction pour attribuer des réductions, on
	 * approxime en faisant une fonction avec PAS_COURBE_REDUCTION paliers.
	 */
	private static final int PAS_COURBE_REDUCTION = 100;
	/**
	 * Lorsqu'on cherche la meilleure fonction pour attribuer des réductions, on
	 * choisi GRANULARITE_COURBE_REDUCTION points auxquelles la dérivée de la
	 * fonction change. plus ce nombre est grand, plus l'estimation de la
	 * meilleure courbe peut être précise mais lente.
	 */
	private static final int GRANULARITE_COURBE_REDUCTION = 10;

	/**
	 * Si les usagers chauffent en moyenne de EDF °C au-dessus de la température
	 * extérieure, on atteint la limite à partir de laquelle le gestionnaire
	 * doit payer plus pour le chauffage.
	 */
	private static double EDF = 3;
	/**
	 * Les usagers doivent payer cette somme plus un supplément s'ils chauffent
	 * tous beaucoup.
	 */
	private static double PLANCHER = 2;

	private double temperatureExterieure;
	private int nombreUsagers;
	private int nombreStrategies;
	/**
	 * Les usagers du logement.
	 */
	private Usager usagers[];
	/**
	 * Les températures choisies par chaque usager. mis-à-jour à la fin de
	 * chaque analyse seulement.
	 */
	private double temperatureUsager[];
	/**
	 * Une approximation par palier de la fonction de réduction selon la
	 * consommation individuelle.
	 */
	private double courbeReduction[];

	/**
	 * @param nombreStrategies
	 *            le nombre de stratégies de chaque usager.
	 * @param nombreEcolos
	 * @param nombrePollueurs
	 * @param nombreVoyageurs
	 */
	public Logement(int nombreStrategies, int nombreEcolos, int nombrePollueurs, int nombreVoyageurs) {

		// On initialise toutes les variables
		temperatureExterieure = 12.5;
		politique = Politique.MONTECARLO;
		methode = Methode.MEILLEUREREPONSE;

		if (nombreStrategies < 2)
			new Exception("Un logement doit proposer au moins 2 stratégies.").printStackTrace();
		if (nombreEcolos < 0 || nombrePollueurs < 0 || nombreVoyageurs < 0)
			new Exception("Un logement ne peut pas contenir un nombre négatif d'usagers d'un profil.")
					.printStackTrace();

		this.nombreStrategies = nombreStrategies;
		nombreUsagers = nombreEcolos + nombrePollueurs + nombreVoyageurs;
		usagers = new Usager[nombreUsagers];

		// On initialise les usagers en fonction de leur type.

		int i;
		int j;

		for (i = 0; i < nombreEcolos; i++)
			usagers[i] = new Ecolo();

		for (j = i; i < j + nombreVoyageurs; i++)
			usagers[i] = new Voyageur();

		for (j = i; i < j + nombrePollueurs; i++)
			usagers[i] = new Pollueur();

		// On initialise les vecteurs stochastiques de chaque usager.
		for (int k = 0; k < nombreUsagers; k++)
			usagers[k].setVecteurStochastique(nombreStrategies);
	}

	public void setTemperatureExterieure(double temp) {
		temperatureExterieure = temp;
	}

	public void setPolitique(Politique p) {
		politique = p;
	}

	public Politique politique() {
		return politique;
	}

	public void setMethode(Methode m) {
		methode = m;
	}

	/**
	 * @param temperature
	 *            la température choisie par un usager.
	 * @return la consommation correspondante.
	 */
	public double consommationIndividuelle(double temperature) {
		return temperature - temperatureExterieure;
	}

	/**
	 * Calcule la température moyenne fixée par chaque usager en fonction d'un
	 * équilibre de Nash Mixte déjà calculé.
	 */
	public void setTemperatureMoyenneUsagers() {
		temperatureUsager = new double[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			temperatureUsager[i] = 0;
			double[] tmp = usagers[i].vecteurStochastique();
			for (int j = 0; j < nombreStrategies; j++)
				temperatureUsager[i] += strategieToTemperature(j, usagers[i]) * tmp[j];
		}
	}

	public double consommationTotale() {
		double total = 0;
		for (double d : temperatureUsager) {
			total += consommationIndividuelle(d);
		}
		return total;
	}

	/**
	 * voir {@link #PLANCHER} et{@link #EDF}
	 * 
	 * @param consommationTotale
	 * @return
	 */
	public double prixConsommationTotale(double consommationTotale) {
		double total = PLANCHER * nombreUsagers;

		if (consommationTotale < EDF * nombreUsagers)
			return total;

		return total + consommationTotale - EDF * nombreUsagers;
	}

	/**
	 * @return le coût pour le propriétaire. Pour le moment, le coût pour un
	 *         usager, auquel on ajoute le coût des réductions de tous les
	 *         usagers.
	 */
	public double coutProprietaire() {

		// Le propriétaire payer la même facture d'éléctricité que les usagers.
		double tout = prixConsommationTotale(consommationTotale());

		// Il paye en plus les réductions qu'il offre à chaque usager multiplié
		// par les factures de transport de ces usagers.
		for (int i = 0; i < nombreUsagers; i++)
			tout += usagers[i].poidsPrixTransports() * temperatureToReduction(temperatureUsager[i]);

		return tout;
	}

	/**
	 * @param strategie
	 * @param u
	 * @return la température choisie par u sachant sa stratégie.
	 */
	public double strategieToTemperature(int strategie, Usager u) {
		return Usager.TEMPERATURE_MINIMALE
				+ strategie * (u.temperatureIdeale() - Usager.TEMPERATURE_MINIMALE) / (nombreStrategies - 1);
	}

	public double temperatureToReduction(double temperature) {

		switch (politique) {
		case AUCUNEREDUCTION:
			return 0;
		case MONTECARLO:
			// On normalise la température entre 0 et 1 puis on l'arrondie.
			// On regarde ensuite la valeur associée à cette température dans
			// courbeReduction.
			return courbeReduction[(int) (PAS_COURBE_REDUCTION * (temperature - Usager.TEMPERATURE_MINIMALE)
					/ (TEMPERATURE_MAX - Usager.TEMPERATURE_MINIMALE))];
		default:
			new Exception("Pas de politique choisie").printStackTrace();
		case TEST:
			return Math.exp(Usager.TEMPERATURE_MINIMALE - temperature);
		}
	}

	/**
	 * @param vecteurDeStrategies
	 *            un vecteur contenant un entier par usager qui indique la
	 *            stratégie choisie par cet usager.
	 * @param nombreIterations
	 *            le nombre de vecteur de stratégies à tester avant d'abandonner
	 *            la recherche. Il peut ne pas exister de Nash pur.
	 * @return un vecteur de stratégie au même format que vecteurDeStrategies.
	 *         Celui-ci correspond à un équilibre de Nash pur. renvoie null si
	 *         aucun vecteur n'est trouvé au bout d'un certains nombre
	 *         d'itérations.
	 */
	public int[] meilleureReponse(int[] vecteurDeStrategies, int nombreIterations) {

		// On s'arrête si on estime qu'on a déjà chercher suffisamment
		// longtemps.
		if (nombreIterations == 0) {
			new Exception("Impossible de trouver un équilibre de Nash pur avec un algorithme de meilleure réponse");
			return null;
		}

		double facture = factureIndividuelle(vecteurDeStrategies);

		double utiliteMax;
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++)
			strategies[i] = vecteurDeStrategies[i];

		double utilite[] = new double[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {

			// On calcule l'utilité de chaque usager.
			utilite[i] = usagers[i].utiliteTotale(strategieToTemperature(vecteurDeStrategies[i], usagers[i]), facture,
					temperatureToReduction(strategieToTemperature(vecteurDeStrategies[i], usagers[i])));

			utiliteMax = utilite[i];

			// On regarde pour chaque usager les utilités qu'il aurait pu
			// obtenir avec ses autres stratégies.
			for (int k = 0; k < nombreStrategies; k++) {

				double tmp = usagers[i]
						.utiliteTotale(strategieToTemperature(k, usagers[i]),
								facture - (consommationIndividuelle(
										strategieToTemperature(vecteurDeStrategies[i], usagers[i])) / nombreUsagers)
								+ (consommationIndividuelle(strategieToTemperature(k, usagers[i])) / nombreUsagers),
						strategieToReduction(k, usagers[i]));

				// Si une stratégie s'avère meilleure que celle utilisée à cette
				// itération, on la mémorise pour la prochaine.
				if (utiliteMax < tmp) {
					strategies[i] = k;
					utiliteMax = tmp;
				}
			}
		}

		// Si un seul usager aurait pu améliorer son utilité avec une stratégie
		// différente, on réitère cet algorithme avec les nouvelles stratégies.
		for (int i = 0; i < nombreUsagers; i++)
			if (strategies[i] != vecteurDeStrategies[i])
				return meilleureReponse(strategies, --nombreIterations);

		// Si aucun usager n'aurait pu améliorer son utilité seul, on est arrivé
		// à un équilibre de Nash et on s'arrête.
		return vecteurDeStrategies;

	}

	/**
	 * Cherche un equilibre de Nash parmi les ensembles de stratégies des
	 * usagers. Calcule l'utilité de tous les ensembles puis cherche un
	 * équilibre de Nash parmi eux. Complexité en O(n^m) avec n le nombre de
	 * stratégies par usager et m le nombre d'usagers.
	 * 
	 * @return l'ensemble de stratégies des joueurs correspondant à un Nash pur.
	 *         null si aucun Nash pur n'est trouvé.
	 */
	public int[] forceBrute() {
		double utilite[][] = new double[(int) Math.pow(nombreStrategies, nombreUsagers)][nombreUsagers];

		int strategies[] = new int[nombreUsagers];
		int aiguille = 0;
		for (int j = 0; j < nombreUsagers; j++)
			strategies[j] = 0;

		// On calcule pour chaque joueur son utilité dans l'ensemble des
		// vecteurs de strategies. Si on a 5 joueurs ayant chacun 10 stratégies,
		// i = 72041 correspond au vecteur de stratégie [1, 4, 0, 2, 7].
		// De même, avec 5 joueurs ayant chacun 6 stratégies, i = 42103 en
		// base 6 correspond au vecteur [3, 0, 1, 2, 4].
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

		// On parcours tous les vecteurs i
		for (int i = 0; i < (int) Math.pow(nombreStrategies, nombreUsagers); i++) {
			nashPur = true;

			// On regarde l'utilité de tous les joueurs j dans le vecteur i
			for (int j = 0; j < nombreUsagers; j++) {
				int decalagej = (int) Math.pow(nombreStrategies, j);
				stratj = Math.floorMod(i, decalagej * nombreStrategies) / decalagej;

				// On regarde toute les stratégies alternatives k pour j
				for (int k = 0; k < nombreStrategies; k++)
					if (utilite[i][j] < utilite[i + (k - stratj) * decalagej][j])
						nashPur = false;
			}
			// Si on tombe sur un Nash pur, on s'arrête.
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
		for (int i = 0; i < nombreUsagers; i++)
			temperatureUsager[i] = strategieToTemperature(vecteur[i], usagers[i]);
	}

	/**
	 * Linear Reward Inaction, cf. internet.
	 * 
	 * @param b
	 *            le facteur de mise-à-jour.
	 * @param utilitePrecedente
	 *            l'utilité obtenue à l'itération précédente de LRI.
	 * @return vrai si on a trouvé un Nash pur probable, faux sinon.
	 */
	public boolean lri(double b, double[] utilitePrecedente) {

		// On choisit une stratégie par usager en fonction de leur vecteur
		// stochastique respectif.
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			double tmp = Math.random();
			strategies[i] = usagers[i].choisirStrategie(tmp);
		}

		double facture = factureIndividuelle(strategies);
		boolean stop = true;

		// On met à jour leur vecteur stochastique et l'utilité précédente
		// devient l'utilité calculée dans les lignes précédentes.
		for (int i = 0; i < nombreUsagers; i++) {
			double utiliteTotale = usagers[i].utiliteTotale(strategieToTemperature(strategies[i], usagers[i]), facture,
					strategieToReduction(strategies[i], usagers[i]));
			if (usagers[i].updateStochastique(strategies[i], utiliteTotale, b, utilitePrecedente[i]) < 1
					- LRI_PRECISION)

				stop = false;
			utilitePrecedente[i] = utiliteTotale;
		}
		return stop;
	}

	/**
	 * détermine le pourcentage de réduction d'un usager en fonction de sa
	 * stratégie.
	 * 
	 * @return une valeur entre 0 et 1.
	 */
	public double strategieToReduction(int strategie, Usager usager) {
		return temperatureToReduction(strategieToTemperature(strategie, usager));
	}

	/**
	 * 
	 * @param strategies
	 *            les stratégies choisies par chaque usager.
	 * @return la facture que chaque usager doit payer.
	 */
	public double factureIndividuelle(int[] strategies) {

		double consommation = 0;
		for (int j = 0; j < nombreUsagers; j++)
			consommation += consommationIndividuelle(strategieToTemperature(strategies[j], usagers[j]));

		// Les usagers doivent payer un montant forfaitaire.
		consommation /= nombreUsagers;
		if (consommation < EDF)
			return PLANCHER;

		// En cas de surconsommation générale, ils doivent payer un supplément.
		return consommation - EDF + PLANCHER;
	}

	/**
	 * affiche le type de chaque usager, la température qu'il a choisie, la
	 * consommation totale des usagers et le cout pour le propriétaire, incluant
	 * les réductions à payer.
	 */
	public void afficherConsommation() {

		for (int i = 0; i < nombreUsagers; i++)
			System.out.println(usagers[i].toString() + " température "
					+ (temperatureExterieure + consommationIndividuelle(temperatureUsager[i])));
		System.out.println("consommation totale " + consommationTotale());

		System.out.println("coût propriétaire " + coutProprietaire());
		System.out.println();
	}

	/**
	 * Analyse la situation en suivant une {@link #methode}. établit les
	 * températures ou températures moyennes de chaque usager.
	 */
	public void analyse() {

		switch (methode) {
		case BRUTEFORCE:
			setTemperatureUsagers(forceBrute());
			return;

		default:
			new Exception("Méthode choisie inexistante, Meilleure réponse choisie").printStackTrace();
		case MEILLEUREREPONSE:
			int[] v = new int[nombreUsagers];
			for (int i = 0; i < nombreUsagers; i++)
				v[i] = 0;
			setTemperatureUsagers(meilleureReponse(v, 10000));
			return;

		case LRI:

			// On initialise

			double b = 0.1;
			boolean nashFound = false;
			int testNash[] = new int[nombreUsagers];

			// Tant que les Nash purs apparents que l'on trouve ne sont pas
			// réellement des Nash pur :
			while (!nashFound) {

				// On initialise les vecteurs stochastiques
				double[] utilite = new double[nombreUsagers];
				for (int i = 0; i < nombreUsagers; i++) {
					utilite[i] = 0;
					usagers[i].setVecteurStochastique(nombreStrategies);
				}

				// On recherche un Nash pur probable.
				while (!lri(b, utilite))
					;

				// Quand on trouve un Nash pur probable, on le teste.
				for (int i = 0; i < nombreUsagers; i++)
					testNash[i] = usagers[i].getPureFromStochastique(LRI_PRECISION);

				// On affine notre recherche, ce qui augmente le temps
				// nécessaire avant d'obtenir un résultat potentiel mais
				// augmente les
				// chances que ce résultat soit un vrai Nash pur.
				nashFound = testMixedNash(testNash);
				b /= 2;
				if (b == 0) {
					LRI_PRECISION /= 10;
					b = 0.01;
				}
			}
			setTemperatureMoyenneUsagers();
			return;
		}
	}

	/**
	 * test si un vecteur de stratégie correspond à un équilibre de Nash pur.
	 * 
	 * @param testNash
	 *            le vecteur de stratégie à tester.
	 * @return vrai si le vecteur est un Nash pur, faux sinon.
	 */
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
	 * initialise la fonction f telle que f(température normalisée) = réduction.
	 * Cette fonction est définie entre 0 et 1, est décroissante dans cette
	 * intervalle. Son domaine est R -> Y avec Y un ensemble discret de valeurs
	 * comprises entre 0 et 1. Y contient {@link #PAS_COURBE_REDUCTION} valeurs.
	 * 
	 * @param max
	 *            f(0) = max. f(1) = 0.
	 * @param seed
	 *            la seed utilisée pour générer aléatoirement la courbe.
	 */
	public void setCourbeReduction(double max, long seed) {
		Random r = new Random(seed);

		// On initialise la courbe pour qu'elle reste à 0 sur [0, 1].
		courbeReduction = new double[PAS_COURBE_REDUCTION];
		for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
			courbeReduction[i] = 0;

		// On choisit des morceaux de courbes et on incrémente leur ordonnée.
		for (int i = 0; i < GRANULARITE_COURBE_REDUCTION; i++)
			courbeReduction[r.nextInt(PAS_COURBE_REDUCTION)]++;

		// On incrémente chaque morceau de courbe de sorte que son ordonnée à la
		// fin de l'étape précédente soit égale à la différence d'ordonnée avec
		// son voisin de droite à la fin de cette étape. On obtient une courbe
		// décroissante telle que f(0) = GRANULARITE_COURBE_REDUCTION et f(1) =
		// 0.
		for (int i = PAS_COURBE_REDUCTION - 1; i > 0; i--)
			courbeReduction[i - 1] += courbeReduction[i];

		// On divise les ordonnées de chaque morceau de courbe
		// proporionnellement pour obtenir une courbe décroissante telle que
		// f(0) = max.
		for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
			courbeReduction[i] /= GRANULARITE_COURBE_REDUCTION / max;
	}

	/**
	 * On cherche la meilleure fonction de réduction ayant un ensemble discret
	 * d'images.
	 * 
	 * @param alphaDistincts
	 *            Les fonctions testées possèdent une valeur maximale, voir
	 *            {@link #setCourbeReduction(double, long)}. On teste des
	 *            fonctions ayant différents maximum réparties équitablement
	 *            entre 0 et 1. On a alphaDistincts valeurs différentes
	 *            possibles.
	 * @param testParAlphaVal
	 *            Pour chaque maximum, on teste ce nombre de fonctions
	 *            distinctes.
	 */
	public void monteCarlo(int alphaDistincts, int testParAlphaVal) {
		Politique pol = politique;
		setPolitique(Politique.AUCUNEREDUCTION);
		analyse();
		double coutMin = coutProprietaire();
		setPolitique(pol);

		double cout;
		long seed;
		long bestSeed = 0;
		double bestAlpha = 0;
		Random rand = new java.util.Random();

		// On cherche la courbe offrant le meilleur résultat.
		// Pour chaque valeur de alpha = k / alphaDistincts,
		for (int k = 1; k <= alphaDistincts; k++)

			// On teste un certain nombre de fonctions de réduction distinctes.
			for (int i = 0; i < testParAlphaVal; i++) {

				// On génère une fonction.
				seed = rand.nextLong();
				setCourbeReduction(k / (double) alphaDistincts, seed);

				// On analyse la situation.
				analyse();
				cout = coutProprietaire();

				// On mémorise la meilleure fonction.
				if (cout < coutMin) {
					coutMin = cout;
					bestAlpha = k / (double) alphaDistincts;
					bestSeed = seed;
				}
			}

		// On recréé la meilleure fonction mémorisée.
		setCourbeReduction(bestAlpha, bestSeed);

		// On écrit dans un ficher texte les coordonnées des points décrivant la
		// meilleure courbe dans le format : à chaque ligne, un couple
		// [température] [réduction]
		PrintWriter pw;
		try {
			pw = new PrintWriter("courbe_de_reduction.txt");
			for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
				pw.println(Usager.TEMPERATURE_MINIMALE
						+ (TEMPERATURE_MAX - Usager.TEMPERATURE_MINIMALE) * i / PAS_COURBE_REDUCTION + " "
						+ courbeReduction[i]);
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		System.out.println("Alpha " + bestAlpha + ", Seed " + bestSeed);
	}

	/**
	 * Dessine une courbe de la fonction de réduction
	 * 
	 * @param g
	 * @param abcisse
	 *            l'abcisse de l'extrêmité gauche de la courbe.
	 * @param ordonnee
	 *            l'ordonnée de l'extrêmité basse de la courbe. (en partant d'en
	 *            bas)
	 * @param c
	 *            la couleur de la courbe.
	 * @param width
	 *            l'abcisse de l'extrêmité droite de la courbe.
	 * @param height
	 *            l'ordonnée de l'extrêmité haute de la courbe. (en partant d'en
	 *            bas)
	 */
	public void drawCourbeReduction(Graphics g, int abcisse, int ordonnee, Color c, int width, int height) {
		g.setColor(c);
		for (int i = 1; i < PAS_COURBE_REDUCTION; i++)
			g.drawLine(abcisse + i * (width - abcisse) / PAS_COURBE_REDUCTION,
					(height - ordonnee) - (int) (courbeReduction[i] * (height - ordonnee)),
					abcisse + (i - 1) * (width - abcisse) / PAS_COURBE_REDUCTION,
					(height - ordonnee) - (int) (courbeReduction[i - 1] * (height - ordonnee)));
	}
}
