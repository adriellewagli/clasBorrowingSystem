package ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;

public class DashboardFrame extends JFrame {
    private String currentUser;
    private String userRole;

    private JLabel lblWelcome;
    private JTable tblEquipment;
    private DefaultTableModel tableModel;

    public DashboardFrame(String fullName, String role) {
        this.currentUser = fullName;
        this.userRole = role;

        setTitle("CLAS Equipment Borrowing System - Dashboard");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main Layout
        setLayout(new BorderLayout());

        // Top Navigation Bar
        JPanel navBar = new JPanel(new BorderLayout());
        navBar.setBackground(new Color(41, 128, 185));
        navBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        lblWelcome = new JLabel("Welcome, " + currentUser + " (" + userRole + ")");
        lblWelcome.setFont(new Font("Arial", Font.BOLD, 16));
        lblWelcome.setForeground(Color.WHITE);
        navBar.add(lblWelcome, BorderLayout.WEST);

        JButton btnLogout = new JButton("Logout");
        btnLogout.setFocusPainted(false);
        btnLogout.addActionListener((ActionEvent e) -> {
            this.dispose();
            new LoginFrame().setVisible(true);
        });
        navBar.add(btnLogout, BorderLayout.EAST);

        add(navBar, BorderLayout.NORTH);

        // Center Panel - Main Content Table
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel lblSectionTitle = new JLabel("Available Equipment Overview");
        lblSectionTitle.setFont(new Font("Arial", Font.BOLD, 14));
        lblSectionTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        contentPanel.add(lblSectionTitle, BorderLayout.NORTH);

        // Table setup
        String[] columnNames = {"ID", "Item Name", "Category", "Serial Number", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Non-editable table cells
            }
        };

        tblEquipment = new JTable(tableModel);
        tblEquipment.setRowHeight(25);
        tblEquipment.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(tblEquipment);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        // Bottom Action Controls Panel
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton btnBorrow = new JButton("Borrow Selected Item");
        JButton btnReturn = new JButton("Return Item");
        JButton btnManageEquipment = new JButton("Manage Equipment");

        // Restrict admin controls based on user role
        if (!"SUPERADMIN".equalsIgnoreCase(userRole) && !"ADMIN".equalsIgnoreCase(userRole)) {
            btnManageEquipment.setEnabled(false); // Only Admins can manage inventory
        }

        actionPanel.add(btnBorrow);
        actionPanel.add(btnReturn);
        actionPanel.add(btnManageEquipment);

        add(actionPanel, BorderLayout.SOUTH);
    }
}