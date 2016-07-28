package logement;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.ParseException;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Une fen�tre permettant de lancer une simulation avec des param�tres choisis.
 */
public class Fenetre {

	public static final int largeurCanvas = 800;
	public static final int hauteurCanvas = 600;

	JFrame frame;
	JFormattedTextField temperatureExterieure;
	JFormattedTextField nombreDeStrategies;
	JFormattedTextField nombreEcolos;
	JFormattedTextField nombrePollueurs;
	JFormattedTextField nombreVoyageurs;
	JComboBox<Logement.Politique> politique;
	JComboBox<Logement.Methode> methode;

	Logement l;

	public Fenetre() {
		frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// On initialise tous les champs du formulaire

		temperatureExterieure = new JFormattedTextField(12.5);
		nombreDeStrategies = new JFormattedTextField(10);
		nombreEcolos = new JFormattedTextField(10);
		nombrePollueurs = new JFormattedTextField(10);
		nombreVoyageurs = new JFormattedTextField(10);

		politique = new JComboBox<>(new Logement.Politique[] { Logement.Politique.MONTECARLO, Logement.Politique.TEST,
				Logement.Politique.AUCUNEREDUCTION });
		methode = new JComboBox<>(new Logement.Methode[] { Logement.Methode.MEILLEUREREPONSE,
				Logement.Methode.BRUTEFORCE, Logement.Methode.LRI });

		// On les met dans la fen�tre

		frame.setLayout(new GridLayout(0, 2));

		frame.add(new JLabel("temp�rature ext�rieure : "));
		frame.add(temperatureExterieure, BorderLayout.CENTER);

		frame.add(new JLabel("Nombre de strat�gies pour chaque joueur : "));
		frame.add(nombreDeStrategies, BorderLayout.CENTER);

		frame.add(new JLabel("nombre d'�cologistes : "));
		frame.add(nombreEcolos, BorderLayout.CENTER);

		frame.add(new JLabel("nombre de pollueurs : "));
		frame.add(nombrePollueurs, BorderLayout.CENTER);

		frame.add(new JLabel("nombre de voyageurs : "));
		frame.add(nombreVoyageurs, BorderLayout.CENTER);

		frame.add(new JLabel("politique de r�duction : "));
		frame.add(politique, BorderLayout.CENTER);

		frame.add(new JLabel("m�thode de recherche de Nash : "));
		frame.add(methode, BorderLayout.CENTER);

		// On finalise la cr�ation de la fen�tre

		JButton ok = new JButton("OK");
		ok.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {

				// Lorsque le bouton "OK" est cliqu�, on lance la simulation.

				// On commence par mettre � jour le contenu des champs.
				try {
					temperatureExterieure.commitEdit();
					nombreDeStrategies.commitEdit();
					nombreEcolos.commitEdit();
					nombrePollueurs.commitEdit();
					nombreVoyageurs.commitEdit();
				} catch (ParseException e1) {
					e1.printStackTrace();
				}

				// On initialise le logement en fonction des contenus des
				// diff�rents champs.

				l = new Logement((int) nombreDeStrategies.getValue(), (int) nombreEcolos.getValue(),
						(int) nombrePollueurs.getValue(), (int) nombreVoyageurs.getValue());

				l.setPolitique((Logement.Politique) politique.getSelectedItem());
				l.setTemperatureExterieure((double) temperatureExterieure.getValue());
				l.setMethode((Logement.Methode) methode.getSelectedItem());

				// On lance la simulation

				if (l.politique() == Logement.Politique.MONTECARLO) {
					l.monteCarlo(10, 10);

					// Si on cherche une courbe de r�duction en suivant la
					// m�thode de Monte-Carlo, on trace cette courbe apr�s
					// l'avoir trouv�e.

					JFrame dessin = new JFrame();
					JCanvas canvas = new JCanvas(l);
					canvas.setPreferredSize(new Dimension(largeurCanvas, hauteurCanvas));
					dessin.add(canvas);
					dessin.pack();
					dessin.setVisible(true);

				} else
					l.analyse();

				// On �crit dans la console les comportements de chaque
				// usager lorsqu'il y a une r�duction et lorsqu'il n'y en a pas
				// et les gains obtenus par le gestionnaire gr�ce � la
				// r�duction.

				double cmp;
				l.analyse();
				l.afficherConsommation();
				cmp = l.coutProprietaire();
				l.setPolitique(Logement.Politique.AUCUNEREDUCTION);
				l.analyse();
				System.out.println("Comportements des usagers sans r�duction : ");
				l.afficherConsommation();
				System.out.println("gain d� � la r�duction " + (l.coutProprietaire() - cmp));
			}
		});
		frame.add(ok);

		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	/**
	 * Une fen�tre dans laquelle une courbe est dessiner avec une �chelle et des
	 * labels.
	 */
	public class JCanvas extends JPanel {
		private static final long serialVersionUID = 1L;
		Logement l;

		public JCanvas(Logement l) {
			this.l = l;
		}

		@Override
		public void paint(Graphics g) {
			super.paint(g);
			BufferedImage image;
			try {
				image = ImageIO.read(this.getClass().getClassLoader().getResourceAsStream("swing.png"));
				g.drawImage(image, 0, 0, largeurCanvas, hauteurCanvas, null);
			} catch (IOException e) {
				e.printStackTrace();
			}
			l.drawCourbeReduction(g, 130, 62, Color.RED, largeurCanvas, hauteurCanvas);
		}

	}

	public static void main(String argv[]) {
		new Fenetre();
	}
}
