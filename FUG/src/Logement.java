public class Logement {

	private static double EDF = 1;

	private int nombreUsagers;
	private int nombreStrategies;
	private Usager usagers[];
	private double temperatureUsager[];

	public Logement(int nu, int nombreStrategies, double proportionEcolos, double proportionPollueurs,
			double proportionVoyageurs) {

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

	public static double consommationIndividuelle(double temperature) {
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

	public double coutProprietaire() {
		double tout = consommationTotale();
		if (tout <= EDF * nombreUsagers)
			tout = 0;
		else
			tout = tout - EDF * nombreUsagers;

		for (int i = 0; i < nombreUsagers; i++)
			tout += usagers[i].poidsPrixTransports() * temperatureToReduction(temperatureUsager[i]);

		return tout;
	}

	public double strategieToTemperature(int strat, Usager u) {
		return Usager.TEMPERATURE_MINIMALE
				+ strat * (u.temperatureIdeale() - Usager.TEMPERATURE_MINIMALE) / (nombreStrategies - 1);
	}

	public double temperatureToReduction(double temperature) {
		return Math.exp(Usager.TEMPERATURE_MINIMALE - temperature);
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
	public int[] parcours(int[] vecteurDeStrategies, int it) throws Exception {

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
				return parcours(strategies, --it);

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

	public double mixedNash() {
		int strategies[] = new int[nombreUsagers];
		for (int i = 0; i < nombreUsagers; i++) {
			double tmp = Math.random();
			strategies[i] = usagers[i].choisirStrategie(tmp);
		}

		double facture = factureIndividuelle(strategies);

		double changement = 0;
		double sum = 0;
		for (int i = 0; i < nombreUsagers; i++) {
			changement += usagers[i].updateStochastique(strategies[i],
					usagers[i].utiliteTotale(strategieToTemperature(strategies[i], usagers[i]), facture,
							strategieToReduction(strategies[i], usagers[i])));
			sum += usagers[i].mixedNashSum();
		}

		return changement / sum;

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

	public static void main(String args[]) {
		// Nash pur

		Logement l = new Logement(5, 10, 50, 50, 50);
		l.setTemperatureUsagers(l.nashPur());

		l.afficherConsommation();

		// Parcours
		l = new Logement(150, 100, 50, 50, 50);
		int[] v = new int[l.nombreUsagers];
		for (int i = 0; i < l.nombreUsagers; i++)
			v[i] = 0;
		try {
			l.setTemperatureUsagers(l.parcours(v, 10000));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		l.afficherConsommation();

		// Mixed Nash

		l = new Logement(1, 10, 50, 0, 0);

		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - startTime < 1000)
			l.mixedNash();
		l.setTemperatureMoyenneUsagers();
		l.afficherConsommation();
	}
}
