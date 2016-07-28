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
		 * Les usagers n'ont jamais de r�duction.
		 */
		AUCUNEREDUCTION,
		/**
		 * La fonction de r�duction optimale pour le gestionnaire est calcul�e
		 * suivant un algorithme de Monte-Carlo.
		 */
		MONTECARLO,
		/**
		 * La fonction de r�duction est une exponentielle invers�e.
		 */
		TEST
	}

	/**
	 * {@link #methode}
	 */
	public enum Methode {
		/**
		 * On calcule toutes les combinaisons de strat�gies et on trouve parmi
		 * elles un �quilibre de Nash pur. tr�s lent.
		 */
		BRUTEFORCE,
		/**
		 * Les usagers choisissent une strat�gie, calcule leur utilit�, observe
		 * la situation, et choisissent � l'it�ration suivante la strat�gie qui
		 * aurait donn� la meilleure utilit� � cette it�ration. On s'arr�te
		 * lorsqu'on trouve un Nash pur. Bonnes performances.
		 */
		MEILLEUREREPONSE,
		/**
		 * Linear Reward Inaction cf. internet. Lent mais am�liorable.
		 */
		LRI
	}

	/**
	 * La politique que suit le gestionnaire pour attribuer des r�ductions aux
	 * usagers, parmi : {@link #AUCUNEREDUCTION}, {@link #MONTECARLO},
	 * {@link #TEST}.
	 */
	private Politique politique;

	/**
	 * La m�thode choisie pour trouver un �quilibre de Nash dans le jeu entre
	 * les usagers. Parmi : {@link #BRUTEFORCE}, {@link #MEILLEUREREPONSE},
	 * {@link #LRI}.
	 */
	private Methode methode;

	/**
	 * La temp�rature maximale � laquelle les usagers peuvent chauffer leur
	 * logement.
	 */
	private static final double TEMPERATURE_MAX = 25;
	/**
	 * dans un algorithme LRI, si pour chaque usager, il existe une strat�gie de
	 * probabilit� proche de 1 (distance < LRI_PRECISION), on teste s'il s'agit
	 * d'un Nash pur potentiel.
	 */
	private static double LRI_PRECISION = 0.001;
	/**
	 * Lorsqu'on cherche la meilleure fonction pour attribuer des r�ductions, on
	 * approxime en faisant une fonction avec PAS_COURBE_REDUCTION paliers.
	 */
	private static final int PAS_COURBE_REDUCTION = 100;
	/**
	 * Lorsqu'on cherche la meilleure fonction pour attribuer des r�ductions, on
	 * choisi GRANULARITE_COURBE_REDUCTION points auxquelles la d�riv�e de la
	 * fonction change. plus ce nombre est grand, plus l'estimation de la
	 * meilleure courbe peut �tre pr�cise mais lente.
	 */
	private static final int GRANULARITE_COURBE_REDUCTION = 10;

	/**
	 * Si les usagers chauffent en moyenne de EDF �C au-dessus de la temp�rature
	 * ext�rieure, on atteint la limite � partir de laquelle le gestionnaire
	 * doit payer plus pour le chauffage.
	 */
	private static double EDF = 3;
	/**
	 * Les usagers doivent payer cette somme plus un suppl�ment s'ils chauffent
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
	 * Les temp�ratures choisies par chaque usager. mis-�-jour � la fin de
	 * chaque analyse seulement.
	 */
	private double temperatureUsager[];
	/**
	 * Une approximation par palier de la fonction de r�duction selon la
	 * consommation individuelle.
	 */
	private double courbeReduction[];

	/**
	 * @param nombreStrategies
	 *            le nombre de strat�gies de chaque usager.
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
			new Exception("Un logement doit proposer au moins 2 strat�gies.").printStackTrace();
		if (nombreEcolos < 0 || nombrePollueurs < 0 || nombreVoyageurs < 0)
			new Exception("Un logement ne peut pas contenir un nombre n�gatif d'usagers d'un profil.")
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
	 *            la temp�rature choisie par un usager.
	 * @return la consommation correspondante.
	 */
	public double consommationIndividuelle(double temperature) {
		return temperature - temperatureExterieure;
	}

	/**
	 * Calcule la temp�rature moyenne fix�e par chaque usager en fonction d'un
	 * �quilibre de Nash Mixte d�j� calcul�.
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
	 * @return le co�t pour le propri�taire. Pour le moment, le co�t pour un
	 *         usager, auquel on ajoute le co�t des r�ductions de tous les
	 *         usagers.
	 */
	public double coutProprietaire() {

		// Le propri�taire payer la m�me facture d'�l�ctricit� que les usagers.
		double tout = prixConsommationTotale(consommationTotale());

		// Il paye en plus les r�ductions qu'il offre � chaque usager multipli�
		// par les factures de transport de ces usagers.
		for (int i = 0; i < nombreUsagers; i++)
			tout += usagers[i].poidsPrixTransports() * temperatureToReduction(temperatureUsager[i]);

		return tout;
	}

	/**
	 * @param strategie
	 * @param u
	 * @return la temp�rature choisie par u sachant sa strat�gie.
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
			// On normalise la temp�rature entre 0 et 1 puis on l'arrondie.
			// On regarde ensuite la valeur associ�e � cette temp�rature dans
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
	 *            strat�gie choisie par cet usager.
	 * @param nombreIterations
	 *            le nombre de vecteur de strat�gies � tester avant d'abandonner
	 *            la recherche. Il peut ne pas exister de Nash pur.
	 * @return un vecteur de strat�gie au m�me format que vecteurDeStrategies.
	 *         Celui-ci correspond � un �quilibre de Nash pur. renvoie null si
	 *         aucun vecteur n'est trouv� au bout d'un certains nombre
	 *         d'it�rations.
	 */
	public int[] meilleureReponse(int[] vecteurDeStrategies, int nombreIterations) {

		// On s'arr�te si on estime qu'on a d�j� chercher suffisamment
		// longtemps.
		if (nombreIterations == 0) {
			new Exception("Impossible de trouver un �quilibre de Nash pur avec un algorithme de meilleure r�ponse");
			return null;
		}

		double facture = factureIndividuelle(vecteurDeStrategies);

		double utiliteMax;
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++)
			strategies[i] = vecteurDeStrategies[i];

		double utilite[] = new double[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {

			// On calcule l'utilit� de chaque usager.
			utilite[i] = usagers[i].utiliteTotale(strategieToTemperature(vecteurDeStrategies[i], usagers[i]), facture,
					temperatureToReduction(strategieToTemperature(vecteurDeStrategies[i], usagers[i])));

			utiliteMax = utilite[i];

			// On regarde pour chaque usager les utilit�s qu'il aurait pu
			// obtenir avec ses autres strat�gies.
			for (int k = 0; k < nombreStrategies; k++) {

				double tmp = usagers[i]
						.utiliteTotale(strategieToTemperature(k, usagers[i]),
								facture - (consommationIndividuelle(
										strategieToTemperature(vecteurDeStrategies[i], usagers[i])) / nombreUsagers)
								+ (consommationIndividuelle(strategieToTemperature(k, usagers[i])) / nombreUsagers),
						strategieToReduction(k, usagers[i]));

				// Si une strat�gie s'av�re meilleure que celle utilis�e � cette
				// it�ration, on la m�morise pour la prochaine.
				if (utiliteMax < tmp) {
					strategies[i] = k;
					utiliteMax = tmp;
				}
			}
		}

		// Si un seul usager aurait pu am�liorer son utilit� avec une strat�gie
		// diff�rente, on r�it�re cet algorithme avec les nouvelles strat�gies.
		for (int i = 0; i < nombreUsagers; i++)
			if (strategies[i] != vecteurDeStrategies[i])
				return meilleureReponse(strategies, --nombreIterations);

		// Si aucun usager n'aurait pu am�liorer son utilit� seul, on est arriv�
		// � un �quilibre de Nash et on s'arr�te.
		return vecteurDeStrategies;

	}

	/**
	 * Cherche un equilibre de Nash parmi les ensembles de strat�gies des
	 * usagers. Calcule l'utilit� de tous les ensembles puis cherche un
	 * �quilibre de Nash parmi eux. Complexit� en O(n^m) avec n le nombre de
	 * strat�gies par usager et m le nombre d'usagers.
	 * 
	 * @return l'ensemble de strat�gies des joueurs correspondant � un Nash pur.
	 *         null si aucun Nash pur n'est trouv�.
	 */
	public int[] forceBrute() {
		double utilite[][] = new double[(int) Math.pow(nombreStrategies, nombreUsagers)][nombreUsagers];

		int strategies[] = new int[nombreUsagers];
		int aiguille = 0;
		for (int j = 0; j < nombreUsagers; j++)
			strategies[j] = 0;

		// On calcule pour chaque joueur son utilit� dans l'ensemble des
		// vecteurs de strategies. Si on a 5 joueurs ayant chacun 10 strat�gies,
		// i = 72041 correspond au vecteur de strat�gie [1, 4, 0, 2, 7].
		// De m�me, avec 5 joueurs ayant chacun 6 strat�gies, i = 42103 en
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

		// Maintenant qu'on a tous les vecteurs d'utilit�, on va voir si l'un
		// d'eux est un Nash pur.
		boolean nashPur;
		int stratj;

		// On parcours tous les vecteurs i
		for (int i = 0; i < (int) Math.pow(nombreStrategies, nombreUsagers); i++) {
			nashPur = true;

			// On regarde l'utilit� de tous les joueurs j dans le vecteur i
			for (int j = 0; j < nombreUsagers; j++) {
				int decalagej = (int) Math.pow(nombreStrategies, j);
				stratj = Math.floorMod(i, decalagej * nombreStrategies) / decalagej;

				// On regarde toute les strat�gies alternatives k pour j
				for (int k = 0; k < nombreStrategies; k++)
					if (utilite[i][j] < utilite[i + (k - stratj) * decalagej][j])
						nashPur = false;
			}
			// Si on tombe sur un Nash pur, on s'arr�te.
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
	 *            le facteur de mise-�-jour.
	 * @param utilitePrecedente
	 *            l'utilit� obtenue � l'it�ration pr�c�dente de LRI.
	 * @return vrai si on a trouv� un Nash pur probable, faux sinon.
	 */
	public boolean lri(double b, double[] utilitePrecedente) {

		// On choisit une strat�gie par usager en fonction de leur vecteur
		// stochastique respectif.
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			double tmp = Math.random();
			strategies[i] = usagers[i].choisirStrategie(tmp);
		}

		double facture = factureIndividuelle(strategies);
		boolean stop = true;

		// On met � jour leur vecteur stochastique et l'utilit� pr�c�dente
		// devient l'utilit� calcul�e dans les lignes pr�c�dentes.
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
	 * d�termine le pourcentage de r�duction d'un usager en fonction de sa
	 * strat�gie.
	 * 
	 * @return une valeur entre 0 et 1.
	 */
	public double strategieToReduction(int strategie, Usager usager) {
		return temperatureToReduction(strategieToTemperature(strategie, usager));
	}

	/**
	 * 
	 * @param strategies
	 *            les strat�gies choisies par chaque usager.
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

		// En cas de surconsommation g�n�rale, ils doivent payer un suppl�ment.
		return consommation - EDF + PLANCHER;
	}

	/**
	 * affiche le type de chaque usager, la temp�rature qu'il a choisie, la
	 * consommation totale des usagers et le cout pour le propri�taire, incluant
	 * les r�ductions � payer.
	 */
	public void afficherConsommation() {

		for (int i = 0; i < nombreUsagers; i++)
			System.out.println(usagers[i].toString() + " temp�rature "
					+ (temperatureExterieure + consommationIndividuelle(temperatureUsager[i])));
		System.out.println("consommation totale " + consommationTotale());

		System.out.println("co�t propri�taire " + coutProprietaire());
		System.out.println();
	}

	/**
	 * Analyse la situation en suivant une {@link #methode}. �tablit les
	 * temp�ratures ou temp�ratures moyennes de chaque usager.
	 */
	public void analyse() {

		switch (methode) {
		case BRUTEFORCE:
			setTemperatureUsagers(forceBrute());
			return;

		default:
			new Exception("M�thode choisie inexistante, Meilleure r�ponse choisie").printStackTrace();
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
			// r�ellement des Nash pur :
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
				// n�cessaire avant d'obtenir un r�sultat potentiel mais
				// augmente les
				// chances que ce r�sultat soit un vrai Nash pur.
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
	 * test si un vecteur de strat�gie correspond � un �quilibre de Nash pur.
	 * 
	 * @param testNash
	 *            le vecteur de strat�gie � tester.
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
	 * initialise la fonction f telle que f(temp�rature normalis�e) = r�duction.
	 * Cette fonction est d�finie entre 0 et 1, est d�croissante dans cette
	 * intervalle. Son domaine est R -> Y avec Y un ensemble discret de valeurs
	 * comprises entre 0 et 1. Y contient {@link #PAS_COURBE_REDUCTION} valeurs.
	 * 
	 * @param max
	 *            f(0) = max. f(1) = 0.
	 * @param seed
	 *            la seed utilis�e pour g�n�rer al�atoirement la courbe.
	 */
	public void setCourbeReduction(double max, long seed) {
		Random r = new Random(seed);

		// On initialise la courbe pour qu'elle reste � 0 sur [0, 1].
		courbeReduction = new double[PAS_COURBE_REDUCTION];
		for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
			courbeReduction[i] = 0;

		// On choisit des morceaux de courbes et on incr�mente leur ordonn�e.
		for (int i = 0; i < GRANULARITE_COURBE_REDUCTION; i++)
			courbeReduction[r.nextInt(PAS_COURBE_REDUCTION)]++;

		// On incr�mente chaque morceau de courbe de sorte que son ordonn�e � la
		// fin de l'�tape pr�c�dente soit �gale � la diff�rence d'ordonn�e avec
		// son voisin de droite � la fin de cette �tape. On obtient une courbe
		// d�croissante telle que f(0) = GRANULARITE_COURBE_REDUCTION et f(1) =
		// 0.
		for (int i = PAS_COURBE_REDUCTION - 1; i > 0; i--)
			courbeReduction[i - 1] += courbeReduction[i];

		// On divise les ordonn�es de chaque morceau de courbe
		// proporionnellement pour obtenir une courbe d�croissante telle que
		// f(0) = max.
		for (int i = 0; i < PAS_COURBE_REDUCTION; i++)
			courbeReduction[i] /= GRANULARITE_COURBE_REDUCTION / max;
	}

	/**
	 * On cherche la meilleure fonction de r�duction ayant un ensemble discret
	 * d'images.
	 * 
	 * @param alphaDistincts
	 *            Les fonctions test�es poss�dent une valeur maximale, voir
	 *            {@link #setCourbeReduction(double, long)}. On teste des
	 *            fonctions ayant diff�rents maximum r�parties �quitablement
	 *            entre 0 et 1. On a alphaDistincts valeurs diff�rentes
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

		// On cherche la courbe offrant le meilleur r�sultat.
		// Pour chaque valeur de alpha = k / alphaDistincts,
		for (int k = 1; k <= alphaDistincts; k++)

			// On teste un certain nombre de fonctions de r�duction distinctes.
			for (int i = 0; i < testParAlphaVal; i++) {

				// On g�n�re une fonction.
				seed = rand.nextLong();
				setCourbeReduction(k / (double) alphaDistincts, seed);

				// On analyse la situation.
				analyse();
				cout = coutProprietaire();

				// On m�morise la meilleure fonction.
				if (cout < coutMin) {
					coutMin = cout;
					bestAlpha = k / (double) alphaDistincts;
					bestSeed = seed;
				}
			}

		// On recr�� la meilleure fonction m�moris�e.
		setCourbeReduction(bestAlpha, bestSeed);

		// On �crit dans un ficher texte les coordonn�es des points d�crivant la
		// meilleure courbe dans le format : � chaque ligne, un couple
		// [temp�rature] [r�duction]
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
	 * Dessine une courbe de la fonction de r�duction
	 * 
	 * @param g
	 * @param abcisse
	 *            l'abcisse de l'extr�mit� gauche de la courbe.
	 * @param ordonnee
	 *            l'ordonn�e de l'extr�mit� basse de la courbe. (en partant d'en
	 *            bas)
	 * @param c
	 *            la couleur de la courbe.
	 * @param width
	 *            l'abcisse de l'extr�mit� droite de la courbe.
	 * @param height
	 *            l'ordonn�e de l'extr�mit� haute de la courbe. (en partant d'en
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
