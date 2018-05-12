package com.muksihs.steembot.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

public class AppWindow {
	private JFrame frame;
	private JScrollPane scrollPane;
	private JTextPane txtpnStartup;

	public AppWindow(String title) {
		if (GraphicsEnvironment.isHeadless()) {
			return;
		}

		GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		int screen_width = gd.getDisplayMode().getWidth();
		int width = screen_width * 87 / 100;
		int screen_height = gd.getDisplayMode().getHeight();
		int height = screen_height * 87 / 100;

		frame = new JFrame();
		frame.setTitle(title);
		frame.setBounds((screen_width - width) / 2, (screen_height - height) / 2, width, height);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		scrollPane = new JScrollPane();
		frame.getContentPane().add(scrollPane, BorderLayout.CENTER);

		txtpnStartup = new JTextPane();
		scrollPane.setViewportView(txtpnStartup);

		MessageConsole mc = new MessageConsole(txtpnStartup);
		mc.redirectOut(Color.BLUE, System.out);
		mc.redirectErr(Color.RED, System.err);
		frame.setVisible(true);
	}
}
