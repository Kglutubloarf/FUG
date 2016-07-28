package usager;

import logement.Logement;

public class Fou extends Usager {

	public Fou() {
		super(Usager.TEMPERATURE_MINIMALE + Math.random() * (Logement.TEMPERATURE_MAX - Usager.TEMPERATURE_MINIMALE),
				Math.random(), Math.random(), Math.random());
	}

	@Override
	public String toString() {
		return "Fou";
	}

}
