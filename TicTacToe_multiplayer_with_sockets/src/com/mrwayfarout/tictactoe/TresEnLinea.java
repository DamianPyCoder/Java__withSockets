package com.mrwayfarout.tictactoe;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.List;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.net.InetAddress;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.omg.CORBA.Request;

//Clase principal
public class TresEnLinea implements Runnable {

	//Declaracion de Variables	
	private String ip = "localhost";
	private int puerto = 2222;
	private Scanner scanner = new Scanner(System.in);
	private JFrame frame;
	private final int ANCHO = 506;
	private final int ALTO = 527;
	private Thread thread;

	private Painter painter;
	
	//Variables de conexion mediante socket	
	private Socket socket;
	private DataOutputStream dos;
	private DataInputStream dis;

	private ServerSocket serverSocket;

	//variables para la creacion del tablero
	private BufferedImage board;
	private BufferedImage Xrojo;
	private BufferedImage Xazul;
	private BufferedImage CirculoRojo;
	private BufferedImage CirculoAzul;

	private String[] spaces = new String[9];

	//Variables para el desarrollo del juego
	private boolean yourTurn = false;
	private boolean circulo = true;
	private boolean accepted = false;
	private boolean unableToCommunicateWithOpponent = false;
	private boolean win = false;
	private boolean enemyWin = false;
	private boolean tie = false;

	private int lengthOfSpace = 160;
	private int errors = 0;
	private int firstSpot = -1;
	private int secondSpot = -1;

	private Font font = new Font("Verdana", Font.BOLD, 32);
	private Font smallerFont = new Font("Verdana", Font.BOLD, 20);
	private Font largerFont = new Font("Verdana", Font.BOLD, 50);

	//Variables de resultados
	private String waitingString = "Esperando al otro jugador";
	private String unableToCommunicateWithOpponentString = "Imposible comunicar con el oponente";//"Unable to communicate with opponent.";
	private String winString = "Ganaste!";
	private String enemyWinString = "Gano el oponente!";
	private String tieString = "Empate";
	
	String MAC = getMACAddress("wlan0");//wlan0 eth0
    String IP = getIPAddress(true);
	
	 
 
	private int[][] wins = new int[][] { { 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 }, { 0, 3, 6 }, { 1, 4, 7 }, { 2, 5, 8 }, { 0, 4, 8 }, { 2, 4, 6 } };

	/**
	 * <pre>
	 * 0, 1, 2 
	 * 3, 4, 5 
	 * 6, 7, 8
	 * </pre>
	 */
	
	public static String getMACAddress(String interfaceName) {
        try {
            ArrayList<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (interfaceName != null) {
                    if (!intf.getName().equalsIgnoreCase(interfaceName)) continue;
                }
                byte[] mac = intf.getHardwareAddress();
                if (mac==null) return "";
                StringBuilder buf = new StringBuilder();
                for (int idx=0; idx<mac.length; idx++)
                    buf.append(String.format("%02X:", mac[idx]));
                if (buf.length()>0) buf.deleteCharAt(buf.length()-1);
                return buf.toString();
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
        /*try {
            // this is so Linux hack
            return loadFileAsString("/sys/class/net/" +interfaceName + "/address").toUpperCase().trim();
        } catch (IOException ex) {
            return null;
        }*/
    }

    public static String getIPAddress(boolean useIPv4) {
        try {
            ArrayList<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                ArrayList<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
    }
	
	//Validamos la direccion IP de nuestro servidor y el puerto de conexion
	public TresEnLinea() {
		System.out.println("Ingrese el IP: ");
		ip = scanner.nextLine();
		System.out.println("Ingrese el puerto: ");
		puerto = scanner.nextInt();
		while (puerto < 1 || puerto > 65535) {
			System.out.println("El puerto ingresado es invalido, por favor ingrese otro puerto");
			puerto = scanner.nextInt();
		}
		
		
		//Inicializamos los valores que tendra el tablero de juego
		loadImages();

		painter = new Painter();
		painter.setPreferredSize(new Dimension(ANCHO, ALTO));

		//InetAddress dir = InetAddress.getLocalHost();		
		//dirIp = dir.getHostAddress();
				
		if (!connect()) initializeServer();

		
		frame = new JFrame();
		frame.setTitle("Tres-En-Linea" + "        " + IP + "     " + MAC );
		frame.setContentPane(painter);
		frame.setSize(ANCHO, ALTO);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setVisible(true);

		thread = new Thread(this, "TresEnLinea");
		thread.start();
	}

	public void run() {
		while (true) {
			tick();
			painter.repaint();

			if (!circulo && !accepted) {
				listenForServerRequest();
			}

		}
	}

	//Dibujamos el tablero
	private void render(Graphics g) {
		g.drawImage(board, 0, 0, null);
		if (unableToCommunicateWithOpponent) {
			g.setColor(Color.RED);
			g.setFont(smallerFont);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int stringWidth = g2.getFontMetrics().stringWidth(unableToCommunicateWithOpponentString);
			g.drawString(unableToCommunicateWithOpponentString, ANCHO / 2 - stringWidth / 2, ALTO / 2);
			return;
		}
		//Establecemos las reglas del juego y el modo como se desarrollara
		if (accepted) {
			for (int i = 0; i < spaces.length; i++) {
				if (spaces[i] != null) {
					if (spaces[i].equals("X")) {
						if (circulo) {
							g.drawImage(Xrojo, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						} else {
							g.drawImage(Xazul, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						}
					} else if (spaces[i].equals("O")) {
						if (circulo) {
							g.drawImage(CirculoAzul, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						} else {
							g.drawImage(CirculoRojo, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						}
					}
				}
			}
			if (win || enemyWin) {
				Graphics2D g2 = (Graphics2D) g;
				g2.setStroke(new BasicStroke(10));
				g.setColor(Color.BLACK);
				g.drawLine(firstSpot % 3 * lengthOfSpace + 10 * firstSpot % 3 + lengthOfSpace / 2, (int) (firstSpot / 3) * lengthOfSpace + 10 * (int) (firstSpot / 3) + lengthOfSpace / 2, secondSpot % 3 * lengthOfSpace + 10 * secondSpot % 3 + lengthOfSpace / 2, (int) (secondSpot / 3) * lengthOfSpace + 10 * (int) (secondSpot / 3) + lengthOfSpace / 2);

				g.setColor(Color.RED);
				g.setFont(largerFont);
				if (win) {
					int stringWidth = g2.getFontMetrics().stringWidth(winString);
					g.drawString(winString, ANCHO / 2 - stringWidth / 2, ALTO / 2);
				} else if (enemyWin) {
					int stringWidth = g2.getFontMetrics().stringWidth(enemyWinString);
					g.drawString(enemyWinString, ANCHO / 2 - stringWidth / 2, ALTO / 2);
				}
			}
			if (tie) {
				Graphics2D g2 = (Graphics2D) g;
				g.setColor(Color.BLACK);
				g.setFont(largerFont);
				int stringWidth = g2.getFontMetrics().stringWidth(tieString);
				g.drawString(tieString, ANCHO / 2 - stringWidth / 2, ALTO / 2);
			}
		} else {
			g.setColor(Color.RED);
			g.setFont(font);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int stringWidth = g2.getFontMetrics().stringWidth(waitingString);
			g.drawString(waitingString, ANCHO / 2 - stringWidth / 2, ALTO / 2);
		}

	}

	//Establecemos el momento de marcar 	
	private void tick() {
		if (errors >= 10) unableToCommunicateWithOpponent = true;

		if (!yourTurn && !unableToCommunicateWithOpponent) {
			try {
				int space = dis.readInt();
				if (circulo) spaces[space] = "X";
				else spaces[space] = "O";
				checkForEnemyWin();
				checkForTie();
				yourTurn = true;
			} catch (IOException e) {
				e.printStackTrace();
				errors++;
			}
		}
	}

	//Verificamos si es el ganador
	private void checkForWin() {
		for (int i = 0; i < wins.length; i++) {
			if (circulo) {
				if (spaces[wins[i][0]] == "O" && spaces[wins[i][1]] == "O" && spaces[wins[i][2]] == "O") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					win = true;
				}
			} else {
				if (spaces[wins[i][0]] == "X" && spaces[wins[i][1]] == "X" && spaces[wins[i][2]] == "X") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					win = true;
				}
			}
		}
	}

	//Verificamos si el oponente es el ganador
	private void checkForEnemyWin() {
		for (int i = 0; i < wins.length; i++) {
			if (circulo) {
				if (spaces[wins[i][0]] == "X" && spaces[wins[i][1]] == "X" && spaces[wins[i][2]] == "X") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					enemyWin = true;
				}
			} else {
				if (spaces[wins[i][0]] == "O" && spaces[wins[i][1]] == "O" && spaces[wins[i][2]] == "O") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					enemyWin = true;
				}
			}
		}
	}

	//Verificamos si es un empate
	private void checkForTie() {
		for (int i = 0; i < spaces.length; i++) {
			if (spaces[i] == null) {
				return;
			}
		}
		tie = true;
	}

	//Verificamos una solicitud de juego
	private void listenForServerRequest() {
		Socket socket = null;
		try {
			socket = serverSocket.accept();
			dos = new DataOutputStream(socket.getOutputStream());
			dis = new DataInputStream(socket.getInputStream());
			accepted = true;
			System.out.println("EL CLIENTE HA SOLICITADO UNIRSE, Y ES ACEPTADO");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//Establecemos la conexion para poder jugar
	private boolean connect() {
		try {
			socket = new Socket(ip, puerto);
			dos = new DataOutputStream(socket.getOutputStream());
			dis = new DataInputStream(socket.getInputStream());
			accepted = true;
		} catch (IOException e) {
			System.out.println("Iniciando servidor " + ip +":" + puerto);//("No se puede conectar a la direccion: " + ip + " : " + puerto + " | Iniciando Servidor");
			return false;
		}
		System.out.println("Conexion satisfactoria con el servidor");
		return true;
	}

	//Inicializamos el servidor mediante sockets
	private void initializeServer() {
		try {
			serverSocket = new ServerSocket(puerto, 8, InetAddress.getByName(ip));
		} catch (Exception e) {
			e.printStackTrace();
		}
		yourTurn = true;
		circulo = false;
	}

	//Inicializamos los signos que se usaran el juego
	private void loadImages() {
		try {
			board = ImageIO.read(getClass().getResourceAsStream("/board.png"));
			Xrojo = ImageIO.read(getClass().getResourceAsStream("/redX.png"));
			CirculoRojo = ImageIO.read(getClass().getResourceAsStream("/redCircle.png"));
			Xazul = ImageIO.read(getClass().getResourceAsStream("/blueX.png"));
			CirculoAzul = ImageIO.read(getClass().getResourceAsStream("/blueCircle.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//Modulo principal
	@SuppressWarnings("unused")
	//Instanciamos un nuevo juego
	public static void main(String[] args) {		
		
		TresEnLinea tresEnLinea = new TresEnLinea();	
		
	   }
	

	private class Painter extends JPanel implements MouseListener {
		private static final long serialVersionUID = 1L;

		public Painter() {
			setFocusable(true);
			requestFocus();
			setBackground(Color.WHITE);
			addMouseListener(this);
		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			render(g);
		}

		//Movimientos del mouse
		@Override		
		public void mouseClicked(MouseEvent e) {
			if (accepted) {
				if (yourTurn && !unableToCommunicateWithOpponent && !win && !enemyWin) {
					int x = e.getX() / lengthOfSpace;
					int y = e.getY() / lengthOfSpace;
					y *= 3;
					int posicion = x + y;

					if (spaces[posicion] == null) {
						if (!circulo) spaces[posicion] = "X";
						else spaces[posicion] = "O";
						yourTurn = false;
						repaint();
						Toolkit.getDefaultToolkit().sync();

						try {
							dos.writeInt(posicion);
							dos.flush();
						} catch (IOException e1) {
							errors++;
							e1.printStackTrace();
						}

						System.out.println("LOS DATOS FUERON ENVIADOS");
						checkForWin();
						checkForTie();

					}
				}
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {

		}

		@Override
		public void mouseReleased(MouseEvent e) {

		}

		@Override
		public void mouseEntered(MouseEvent e) {

		}

		@Override
		public void mouseExited(MouseEvent e) {

		}

	}

}
