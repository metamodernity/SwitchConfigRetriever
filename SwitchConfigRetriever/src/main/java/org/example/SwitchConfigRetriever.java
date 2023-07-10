package org.example;

import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPClient;

import javax.swing.*;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.SocketException;
import java.util.HashMap;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SwitchConfigRetriever extends JFrame {
    private JButton startButton;
    private JButton infoButton;
    private JTextArea statusTextArea;
    private JTextField subnetField;
    private JTextField startIPField;
    private JTextField endIPField;
    private JTextField loginField;
    private JPasswordField passwordField;
    private JTextArea terminalTextArea;
    private JButton terminalButton;
    private String folderPath = "C:/SwitchConfigs/";
    private volatile boolean isRunning = false;
    public SwitchConfigRetriever() {
        super("Switch Config Retriever");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 400);
        setLocationRelativeTo(null);

        startButton = new JButton("Start");
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isRunning) {
                    isRunning = true;
                    startButton.setText("Stop");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                retrieveSwitchConfigs();
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (isRunning) {
                                        isRunning = false;
                                        startButton.setText("Start");
                                    }
                                }
                            });
                        }
                    }).start();
                }
                else {
                    isRunning = false;
                    startButton.setEnabled(false);
                    startButton.setText("Start");
                    appendStatus("Waiting for check to finish...");
                }
            }
        });

        infoButton = new JButton("Info");
        infoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                infoWindow("Абоба");
            }
        });

        terminalButton = new JButton("Terminal");
        terminalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openTerminal("cd /d C:/");
            }
        });


        statusTextArea = new JTextArea();
        statusTextArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(statusTextArea);

        subnetField = new JTextField("192.168.200"); //"192.168.200"
        ((PlainDocument) subnetField.getDocument()).setDocumentFilter(new IntFilter(true));
        subnetField.setToolTipText("Enter the subnet");

        startIPField = new JTextField("1");
        ((PlainDocument) startIPField.getDocument()).setDocumentFilter(new IntFilter(false));
        startIPField.setToolTipText("Enter the starting IP address");

        endIPField = new JTextField("254");
        ((PlainDocument) endIPField.getDocument()).setDocumentFilter(new IntFilter(false));
        endIPField.setToolTipText("Enter the ending IP address");

        loginField = new JTextField("admin");
        loginField.setToolTipText("Enter the login");

        passwordField = new JPasswordField("QWEqwe12345");
        passwordField.setToolTipText("Enter the password");

        JPanel inputPanel = new JPanel(new GridLayout(5, 2));

        inputPanel.add(new JLabel("Subnet:"));
        inputPanel.add(subnetField);
        inputPanel.add(new JLabel("Start IP:"));
        inputPanel.add(startIPField);
        inputPanel.add(new JLabel("End IP:"));
        inputPanel.add(endIPField);
        inputPanel.add(new JLabel("Login:"));
        inputPanel.add(loginField);
        inputPanel.add(new JLabel("Password:"));
        inputPanel.add(passwordField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(startButton);
        buttonPanel.add(terminalButton);
        buttonPanel.add(infoButton);

        terminalTextArea = new JTextArea();
        terminalTextArea.setEditable(false);

        JScrollPane terminalScrollPane = new JScrollPane(terminalTextArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(inputPanel, BorderLayout.NORTH);
        leftPanel.add(scrollPane, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftPanel);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(buttonPanel, BorderLayout.NORTH);
        rightPanel.add(terminalScrollPane, BorderLayout.CENTER);
        splitPane.setRightComponent(rightPanel);

        add(splitPane, BorderLayout.CENTER);
    }
    // Метод для получения производителя по SNMP
    private String getSwitchManufacturer(String ipAddress) throws IOException {
        String community = "public"; // SNMP community
        String oidSysDescr = "1.3.6.1.2.1.1.1.0"; // OID для sysDescr.0
        String manufacturer = "unknown";

        TransportMapping<? extends Address> transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();

        Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/161");
        CommunityTarget<Address> target = new CommunityTarget<Address>();
        target.setCommunity(new OctetString(community));
        target.setAddress(targetAddress);
        target.setVersion(SnmpConstants.version2c);

        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oidSysDescr)));
        pdu.setType(PDU.GET);

        ResponseEvent<Address> event = snmp.send(pdu, target, null);
        if (event != null && event.getResponse() != null) {
            PDU response = event.getResponse();
            if (response.getErrorStatus() == PDU.noError) {
                VariableBinding vb = response.getVariableBindings().get(0);
                String sysDescr = vb.getVariable().toString();

                // Анализируем описание и возвращаем производителя
                if (sysDescr.contains("Cisco")) {
                    manufacturer = "Cisco";
                } 
                else if (sysDescr.contains("D-Link")) {
                    manufacturer = "D-Link";
                } 
                else if (sysDescr.contains("Juniper")) {
                    manufacturer = "Juniper";
                }
                else if (sysDescr.contains("MikroTik")) {
                    manufacturer = "MikroTik";
                }
            }
        }

        snmp.close();
        return manufacturer;
    }

    private void retrieveSwitchConfigs() throws InterruptedException {
        HashMap<String, String> configMap = new HashMap<>();
        configMap.put("Cisco", "show running-config");
        configMap.put("D-Link", "show config current_config");
        configMap.put("Juniper", "show configuration");
        configMap.put("MikroTik", "/export compact"); // 200.103 для Eltex TFTP для Dlink
        configMap.put("unknown", "show current_config"); // 90% коммутаторов в сети это D-Link

        File folder = new File(this.folderPath);
        if (!folder.exists()) {
            if (folder.mkdir()) {
                appendStatus("SwitchConfigs folder created");
            } else {
                appendStatus("Failed to create SwitchConfigs folder");
                return;
            }
        }

        String subnet = subnetField.getText();
        int startIP = Integer.parseInt(startIPField.getText());
        int endIP = Integer.parseInt(endIPField.getText());
        String login = loginField.getText();
        String password = new String(passwordField.getPassword());

        for (int i = startIP; i <= endIP && isRunning; i++) {
            String ipAddress = subnet + "." + i;
            appendStatus("Checking: " + ipAddress);

            try {
                // Подключение по SNMP для получения производителя
                String manufacturer = getSwitchManufacturer(ipAddress);

                // Создание папки для производителя, если её нет
                String manufacturerFolderPath = folderPath + manufacturer;
                File manufacturerFolder = new File(manufacturerFolderPath);
                if (!manufacturerFolder.exists()) {
                    if (manufacturerFolder.mkdir()) {
                        appendStatus("Created folder for manufacturer: " + manufacturer);
                    } else {
                        appendStatus("Failed to create folder for manufacturer: " + manufacturer);
                        continue;
                    }
                }

                // Получение имени устройства
                String deviceName = getDeviceName(ipAddress);

                // Формирование команды к коммутатору
                String command;
                if (configMap.containsKey(manufacturer)) {
                    command = configMap.get(manufacturer);
                } else {
                    command = configMap.get("unknown");
                }

                TelnetClient telnetClient = new TelnetClient();
                telnetClient.setDefaultTimeout(1500);
                telnetClient.connect(ipAddress, 23);
                appendStatus("Connected to: " + ipAddress);

                InputStream in = telnetClient.getInputStream();
                OutputStream out = telnetClient.getOutputStream();
                threadSleep();
                byte[] buff = new byte[1024];
                int ret_read;

                threadSleep();
                ret_read = in.read(buff);

                if (ret_read > 0) {
                    appendTerminal(new String(buff, 0, ret_read, "UTF-8"));
                }

                PrintWriter writer = new PrintWriter(out, true);

                writer.println(login);
                threadSleep();

                ret_read = in.read(buff);
                if (ret_read > 0) {
                    appendTerminal(new String(buff, 0, ret_read, "UTF-8"));
                }

                writer.println(password);
                threadSleep();

                ret_read = in.read(buff);
                if (ret_read > 0) {
                    appendTerminal(new String(buff, 0, ret_read, "UTF-8"));
                }

                out.write(("\r\n").getBytes());
                out.flush();

                ret_read = in.read(buff);
                if (ret_read > 0) {
                    appendTerminal(new String(buff, 0, ret_read, "UTF-8"));
                }

                threadSleep();

                if (manufacturer != "unknown" && configMap.containsKey(manufacturer)) {
                    // Используется TFTP для передачи конфигурации коммутатора
                    TFTPClient tftpClient = new TFTPClient();
                    tftpClient.setDefaultTimeout(5000); // Установка таймаута
                    String configFileName = deviceName + ".txt";
                    String filePath = manufacturerFolderPath + "/" + configFileName;
                    FileOutputStream fileOutputStream = new FileOutputStream(filePath);
                    tftpClient.open();
                    tftpClient.receiveFile("/config.cfg", TFTP.BINARY_MODE, fileOutputStream, ipAddress);
                    fileOutputStream.close();
                    tftpClient.close();
                    appendStatus("Configuration saved for: " + ipAddress);
                } else {
                    out.write((command + "\r\n").getBytes());
                    out.flush();
                    appendTerminal(command);

                    out.flush();

                    ret_read = in.read(buff);
                    StringBuilder configBuilder = new StringBuilder();
                    while (ret_read >= 0) {
                        if (ret_read > 0) {
                            configBuilder.append(new String(buff, 0, ret_read, "UTF-8"));
                        }
                        ret_read = in.read(buff);
                    }

                    String config = configBuilder.toString();
                    // String configFileName = deviceName + ".txt";
                    String filePath = manufacturerFolderPath + "/" + ipAddress + ".txt";
                    saveConfigurationToFile(config, filePath);
                }

                telnetClient.disconnect();
                appendStatus("Disconnected from: " + ipAddress);
            } catch (SocketException e) {
                appendStatus("Connection failed: " + e.getMessage());
            } catch (IOException e) {
                appendStatus("IO error: " + e.getMessage());
            }
            if (!isRunning){
                startButton.setEnabled(true);
                appendStatus("Stopped.");
            }
        }
    }
    private String getDeviceName(String ipAddress) throws IOException {
        String community = "public"; // SNMP community
        String oidSysName = "1.3.6.1.2.1.1.5.0"; // OID для sysName.0
        String sysName = "unknown";

        TransportMapping<? extends Address> transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();

        Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/161");
        CommunityTarget<Address> target = new CommunityTarget<Address>();
        target.setCommunity(new OctetString(community));
        target.setAddress(targetAddress);
        target.setVersion(SnmpConstants.version2c);

        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oidSysName)));
        pdu.setType(PDU.GET);

        ResponseEvent<Address> event = snmp.send(pdu, target, null);
        if (event != null && event.getResponse() != null) {
            PDU response = event.getResponse();
            if (response.getErrorStatus() == PDU.noError) {
                VariableBinding vb = response.getVariableBindings().get(0);
                sysName = vb.getVariable().toString();
            }
        }
        snmp.close();
        return sysName;
    }

    private void TFTPmethod() throws InterruptedException { //аналог retrieveSwitchConfigs() (пока не используется)
        String subnet = subnetField.getText();
        int startIP = Integer.parseInt(startIPField.getText());
        int endIP = Integer.parseInt(endIPField.getText());

        for (int i = startIP; i <= endIP; i++) {
            String ipAddress = subnet + "." + i;
            appendStatus("Checking: " + ipAddress);

            try {
                TFTPClient tftpClient = new TFTPClient();

                // Установка таймаута
                tftpClient.setDefaultTimeout(5000);


                String localFile = ipAddress + ".txt";
                FileOutputStream fileOutputStream = new FileOutputStream(localFile);

                // Получение конфигурации коммутатора по TFTP
                tftpClient.open();
                tftpClient.receiveFile("/config.cfg", TFTP.BINARY_MODE, fileOutputStream, ipAddress);
                fileOutputStream.close();
                tftpClient.close();

                appendStatus("Configuration saved for: " + ipAddress);

            } catch (IOException e) {
                appendStatus("Failed to retrieve configuration for: " + ipAddress + " - " + e.getMessage());
            }
        }
    }
    private void saveConfigurationToFile(String config, String filePath) {
        try (PrintWriter writer = new PrintWriter(filePath)) {
            writer.write(config);
            appendStatus("Configuration saved to: " + filePath);
        } catch (IOException e) {
            appendStatus("Failed to save configuration: " + e.getMessage());
        }
    }

    private void threadSleep(){
        try {
            Thread.sleep(500);  //Даём потоку подождать
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void openTerminal(String command) {
        try {
            // Создаем процесс, выполняющий команду открытия терминала с заданной командой
            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", command);
            processBuilder.inheritIO(); // Позволяет наследовать ввод/вывод с текущего Java процесса
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void appendStatus(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusTextArea.append(message + "\n");
                statusTextArea.setCaretPosition(statusTextArea.getDocument().getLength()); // Установить каретку в конец текста
            }
        });
    }


    private void appendTerminal(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                terminalTextArea.append(message);
                terminalTextArea.setCaretPosition(terminalTextArea.getDocument().getLength()); // Установить каретку в конец текста
            }
        });
    }


    private void infoWindow(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SwitchConfigRetriever switchConfigRetriever = new SwitchConfigRetriever();
                switchConfigRetriever.setVisible(true);
            }
        });
    }
}
