package UI;

import net.Client;
import net.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

public class ClientGui implements UI {
    private JFrame frame;
    private JPanel chatPanel;
    private JScrollPane chatScrollPane;
    private JTextField messageField;
    private JButton sendButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private Map<String, Color> userColors = new HashMap<>(); // Хранилище цветов
    private JLabel currentUserNameLabel;
    private JLabel lastSentStatusIcon = null;

    private String selectedChat = "General";
    private String selectedUser = null;

    public Client client;
    private String myName = null;
    private final java.util.ArrayList<java.util.function.Consumer<String>> listeners = new java.util.ArrayList<>();

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8435;

    public ClientGui() {
        createAndShowGUI();
        connectToServer();
    }

    // Внутренний класс для текстового поля с закругленными углами
    private static class RoundedTextField extends JTextField {
        private int cornerRadius = 20;

        public RoundedTextField(int columns) {
            super(columns);
            setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
            setFont(new Font("Segoe UI", Font.PLAIN, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Рисуем фон
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

            // Рисуем границу
            g2.setColor(new Color(160, 160, 180));          // Более тёмный и заметный цвет
            g2.setStroke(new java.awt.BasicStroke(2.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);

            g2.dispose();
            super.paintComponent(g);
        }

//        @Override
//        public void setBorder(Border border) {
//            // Игнорируем установку границ для кастомной отрисовки
//        }
    }
    // Внутренний класс для кнопки с закругленными углами
    private static class RoundedButton extends JButton {
        private int cornerRadius = 20;

        public RoundedButton(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setBackground(new Color(0, 120, 215));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Рисуем фон
            if (getModel().isPressed()) {
                g2.setColor(new Color(0, 100, 195));
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(0, 130, 235));
            } else {
                g2.setColor(getBackground());
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
            g2.dispose();

            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            // Не рисуем стандартную границу
        }
    }


    private void createAndShowGUI() {
        frame = new JFrame("TeaSpill");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(new Color(240, 240, 245));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 245));

        // Верхняя панель статуса
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(new Color(240, 240, 245));
        JLabel statusLabel = new JLabel("Сервер: " + SERVER_HOST + ":" + SERVER_PORT);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(0, 100, 0));
        topPanel.add(statusLabel, BorderLayout.WEST);

        currentUserNameLabel = new JLabel("", SwingConstants.RIGHT);
        currentUserNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        currentUserNameLabel.setForeground(new Color(0, 120, 215));
        topPanel.add(currentUserNameLabel, BorderLayout.EAST);


        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Центральная панель
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setBackground(new Color(240, 240, 245));

        // Область чата с пузырьками
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(240, 240, 245));
        chatScrollPane = new JScrollPane(chatPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatScrollPane.setBackground(new Color(240, 240, 245));

        // Список пользователей
        userListModel = new DefaultListModel<>();
        userListModel.addElement("General");
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        userList.setBackground(new Color(250, 250, 255));
        userList.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 210)),
                "Чаты",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));

        userList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {  // Одинарный клик
                    int index = userList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selected = userListModel.get(index);

                        if ("General".equals(selected)) {
                            // Общий чат - просто переключаем
                            selectChat(selected);
                        } else {
                            // Личный чат - вставляем @username: в поле ввода
                            selectChat(selected);
                            messageField.setText("@" + selected + ":");
                            messageField.setForeground(Color.BLACK);
                            messageField.requestFocus();
                            // Выделяем текст после @ для удобства редактирования
                            messageField.select(1, messageField.getText().length());
                        }
                    }
                }
            }
        });

        userList.setSelectedIndex(0);

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(200, 0));
        userScroll.setBorder(BorderFactory.createEmptyBorder());

        splitPane.setLeftComponent(chatScrollPane);
        splitPane.setDividerLocation(650);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Панель чата на всю ширину
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Список пользователей добавляем в правую часть как отдельную панель
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(200, 0));
        rightPanel.setBackground(new Color(240, 240, 245));
        rightPanel.add(userScroll, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        // Нижняя панель ввода
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBackground(new Color(240, 240, 245));
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 80)); // Увеличены отступы по бокам

        // Создаем rounded текстовое поле
        messageField = new RoundedTextField(0);
        messageField.setEnabled(false);
        messageField.setPreferredSize(new Dimension(0, 50));
        // Placeholder
        setPlaceholder(messageField, "Введите сообщение...");

        // Создаем rounded кнопку
        sendButton = new RoundedButton("Отправить");
        sendButton.setEnabled(false);
        sendButton.setPreferredSize(new Dimension(120, 50));
        sendButton.setMinimumSize(new Dimension(120, 50));

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        // === Обработка закрытия ===
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                performLogout();
                frame.dispose(); // Закрываем окно
                System.exit(0);  // Завершаем приложение
            }
        });

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
    }


    private void selectChat(String chatName) {
        if ("General".equals(chatName)) {
            selectedChat = "General";
            selectedUser = null;
            messageField.setText("");
            setPlaceholder(messageField, "Введите сообщение...");
            userList.setSelectedIndex(0);  // Выделяем визуально
        } else {
            selectedChat = "Private";
            selectedUser = chatName;
            messageField.setText("");
            setPlaceholder(messageField, "Личное сообщение для " + chatName + "...");
            // Находим и выделяем выбранного пользователя в списке
            for (int i = 0; i < userListModel.size(); i++) {
                if (userListModel.get(i).equals(chatName)) {
                    userList.setSelectedIndex(i);
                    break;
                }
            }
        }
        messageField.requestFocus();
    }


    // Метод для добавления placeholder
    private void setPlaceholder(JTextField field, String placeholder) {
        field.setForeground(Color.GRAY);
        field.setText(placeholder);

        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setForeground(Color.GRAY);
                    field.setText(placeholder);
                }
            }
        });
    }

    private void connectToServer() {
        try {
            client = new Client(SERVER_HOST, SERVER_PORT);
            client.addDataListener(this::showInfo);
            client.start();
            addSystemMessage("Подключено к серверу " + SERVER_HOST + ":" + SERVER_PORT);
        } catch (IOException e) {
            addSystemMessage("Ошибка подключения: " + e.getMessage());
            JOptionPane.showMessageDialog(frame,
                    "Не удалось подключиться к серверу " + SERVER_HOST + ":" + SERVER_PORT + "\n" +
                            "Убедитесь, что сервер запущен.",
                    "Ошибка подключения",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (!text.isEmpty() && myName != null && !text.equals("Введите сообщение...")) {

            // === СРАЗУ ПОКАЗЫВАЕМ СООБЩЕНИЕ НА ЭКРАНЕ ===
            addMessage(myName, text, true);

            // Формируем сообщение для отправки
            String messageToSend;
            if ("General".equals(selectedChat)) {
                messageToSend = text;
            } else {
                messageToSend = "@" + selectedUser + ":" + text;
            }

            // Отправляем на сервер
            sendToServer(messageToSend);

            messageField.setText("");
            messageField.requestFocus();
        }
    }

    private void sendToServer(String data) {
        for (var listener : listeners) {
            listener.accept(data);
        }
    }

    // Класс для панели со скруглёнными углами
    private static class RoundedPanel extends JPanel {
        private final int cornerRadius;
        private final boolean drawBorder;

        public RoundedPanel(int radius) {
            this(radius, false);
        }

        public RoundedPanel(int radius, boolean drawBorder) {
            super();
            this.cornerRadius = radius;
            this.drawBorder = drawBorder;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Рисуем фон
            if (getBackground().getAlpha() > 0) {
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
            }

            // Рисуем закруглённую рамку если нужно
            if (drawBorder) {
                g2.setColor(new Color(220, 220, 230));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
            }

            g2.dispose();
        }
    }

    private void addMessage(String author, String text, boolean isMine) {
        SwingUtilities.invokeLater(() -> {

            boolean isPrivate = text.startsWith("🔒 ");

            RoundedPanel bubble = new RoundedPanel(15, !isMine); // true = рисовать рамку
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));

            int padding = 8;
            int paddingSide = 10;


            if (isMine) {
                // Мои сообщения
                bubble.setBackground(isPrivate ? new Color(160, 140, 220) : new Color(0, 120, 215));
            } else {
                // Чужие сообщения
                bubble.setBackground(isPrivate ? new Color(245, 235, 210) : Color.WHITE);
            }
            bubble.setBorder(BorderFactory.createEmptyBorder(padding, paddingSide, padding, paddingSide));


            // Имя автора - слева сверху
            JLabel nameLabel = new JLabel(author);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            nameLabel.setForeground(isMine ? new Color(220, 235, 255) : getUserColor(author));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameLabel.setBorder(BorderFactory.createEmptyBorder(-2, 0, 2, 0)); // Уменьшен отступ снизу (было 4)
            bubble.add(nameLabel);

            // Текст сообщения с переносом слов
            JLabel textLabel = new JLabel();
            textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            textLabel.setForeground(isMine ? Color.WHITE : Color.BLACK);
            textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            // HTML-форматирование для переноса текста + фикс. ширина
            String wrappedText = "<html><div style='width: 310px; white-space: normal;'>" +
                    text.replace("<", "&lt;").replace(">", "&gt;") +
                    "</div></html>";
            textLabel.setText(wrappedText);

            bubble.add(textLabel);

            if (isMine) {
                JLabel statusIcon = new JLabel("  ✔", SwingConstants.RIGHT);
                statusIcon.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                statusIcon.setForeground(Color.WHITE); // Светло-голубой/белый цвет
                statusIcon.setAlignmentX(Component.RIGHT_ALIGNMENT);
                statusIcon.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
                bubble.add(statusIcon);

                // Сохраняем ссылку, чтобы потом обновить на две галочки
                lastSentStatusIcon = statusIcon;

                System.out.println("✓ Создана галочка для сообщения: " + text);
            }



            // Фиксированная ширина пузырька
            bubble.setPreferredSize(new Dimension(250, 50));
            bubble.setMaximumSize(new Dimension(250, Short.MAX_VALUE));

            // Контейнер с выравниванием по стороне
            JPanel messageContainer = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 10, 6));
            messageContainer.setBackground(new Color(240, 240, 245));
            messageContainer.setOpaque(true);
            messageContainer.add(bubble);

            // Отступ между сообщениями
            messageContainer.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

            chatPanel.add(messageContainer);
            chatPanel.revalidate();
            chatPanel.repaint();

            // Автопрокрутка вниз
            SwingUtilities.invokeLater(() -> {
                JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            });
        });
    }


    private void addSystemMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            JPanel systemPanel = new JPanel();
            systemPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
            systemPanel.setBackground(new Color(240, 240, 245));

            JLabel systemLabel = new JLabel(text);
            systemLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            systemLabel.setForeground(new Color(120, 120, 140));
            systemLabel.setBackground(new Color(230, 230, 240));
            systemLabel.setOpaque(true);
            systemLabel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

            systemPanel.add(systemLabel);

            chatPanel.add(systemPanel);
            chatPanel.revalidate();
            chatPanel.repaint();
        });
    }

    private void showAuthDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Имя:"), gbc);
        gbc.gridx = 1;
        JTextField nameField = new JTextField(12);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Пароль:"), gbc);
        gbc.gridx = 1;
        JPasswordField passwordField = new JPasswordField(12);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(passwordField, gbc);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Вход",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (name.isEmpty() || password.isEmpty()) {
                addSystemMessage("Ошибка: имя и пароль не могут быть пустыми");
                showAuthDialog();
                return;
            }

            if (!Character.isLetter(name.charAt(0))) {
                addSystemMessage("Ошибка: имя должно начинаться с буквы");
                showAuthDialog();
                return;
            }

            sendToServer("LOGIN:" + name + ":" + password);
        }
    }

    private void showRegisterDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Имя:"), gbc);
        gbc.gridx = 1;
        JTextField nameField = new JTextField(12);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Пароль:"), gbc);
        gbc.gridx = 1;
        JPasswordField passwordField = new JPasswordField(12);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(passwordField, gbc);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Регистрация",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (name.isEmpty() || password.isEmpty()) {
                addSystemMessage("Ошибка: имя и пароль не могут быть пустыми");
                showRegisterDialog();
                return;
            }

            if (!Character.isLetter(name.charAt(0))) {
                addSystemMessage("Ошибка: имя должно начинаться с буквы");
                showRegisterDialog();
                return;
            }

            sendToServer("REGISTER:" + name + ":" + password);
        }
    }

    @Override
    public void showInfo(String data, MessageType type) {
        SwingUtilities.invokeLater(() -> {
            switch (type) {

                case MESSAGE -> {
                    String[] parts = data.split(":", 2);
                    if (parts.length == 2) {
                        String author = parts[0];
                        String text = parts[1];

                        // === Проверка: это личное сообщение? ===
                        boolean isPrivate = text.startsWith("[Личное]");
                        String displayText = text;

                        if (isPrivate) {
                            // Убираем [Личное] и [получатель] для отображения
                            displayText = text.substring("[Личное] ".length());
                            // Если есть [получатель], убираем и его
                            if (displayText.startsWith("[")) {
                                int closeBracket = displayText.indexOf("] ");
                                if (closeBracket > 0) {
                                    displayText = displayText.substring(closeBracket + 2);
                                }
                            }

                            displayText = "🔒 " + displayText;
                        }

                        // === Показываем ВСЕ сообщения (сервер уже отфильтровал) ===
                        // Сервер отправляет только тем, кому нужно
                        boolean isMine = myName != null && myName.equalsIgnoreCase(author);
                        addMessage(author, displayText, isMine);
                    }
                }

                case HISTORY -> {  // <-- ДОБАВЛЕНО: обработка истории
                    String[] parts = data.split(":", 2);
                    if (parts.length == 2) {
                        String author = parts[0];
                        String text = parts[1];
                        boolean isMine = myName != null && myName.equalsIgnoreCase(author);
                        addMessage(author, text, isMine);
                    }
                }
                case USER_LIST -> {  // <-- ДОБАВЛЕНО: обработка списка пользователей
                    String[] users = data.split(",");
                    userListModel.clear();
                    for (String user : users) {
                        if (!user.isEmpty() && !userListModel.contains(user)) {
                            userListModel.addElement(user.toUpperCase());
                        }
                    }
                }

                case READ_ACK -> {
                    System.out.println("Получено подтверждение прочтения");
                    if (lastSentStatusIcon != null) {
                        lastSentStatusIcon.setText("  ✓✓"); // Две галочки
                        System.out.println("✓✓ Галочка обновлена на двойную");
                    } else {
                        System.out.println("lastSentStatusIcon = null");
                    }
                }

                case INFO -> {

                    if (data.startsWith("MY_NAME:")) {
                        myName = data.substring("MY_NAME:".length());
                        System.out.println("✓ myName установлен: " + myName);

                        currentUserNameLabel.setText(myName.toUpperCase());

                        messageField.setEnabled(true);
                        sendButton.setEnabled(true);
                        messageField.requestFocus();

                        return;
                    }

                    if (data.startsWith("Пользователь ") && data.endsWith(" вошел в чат")) {
                        String name = data.substring("Пользователь ".length(), data.length() - " вошел в чат".length());
                        if (myName == null) {
                            myName = name;
                            System.out.println("✓ myName установлен: " + myName);
                            messageField.setEnabled(true);
                            sendButton.setEnabled(true);
                            messageField.requestFocus();
                        }
                        if (!userListModel.contains(name.toUpperCase())) {
                            userListModel.addElement(name.toUpperCase());
                        }
                        addSystemMessage(data);
                    } else if (data.startsWith("Пользователь ") && data.endsWith(" вышел из чата")) {
                        String name = data.substring("Пользователь ".length(), data.length() - " вышел из чата".length());
                        userListModel.removeElement(name);
                        addSystemMessage(data);
                    } else if (data.equals("Регистрация успешна! Теперь войдите.")) {
                        addSystemMessage(" " + data);
                        showAuthDialog();
                    } else {
                        addSystemMessage(data);
                    }
                }
                case ERROR -> {
                    addSystemMessage(" " + data);
                    if (data.contains("имя") || data.contains("пароль") || data.contains("занято")) {
                        showAuthDialog();
                    }
                }
                case REQUEST -> {
                    if (data.contains("Введите имя")) {
                        String[] options = {"Вход", "Регистрация"};
                        int choice = JOptionPane.showOptionDialog(frame,
                                "У вас есть аккаунт?",
                                "SplitTea»",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null,
                                options,
                                options[0]);

                        if (choice == 0) {
                            showAuthDialog();
                        } else {
                            showRegisterDialog();
                        }
                    }
                }
                default -> addSystemMessage(data);
            }
        });
    }

    @Override
    public void addUserDataListener(java.util.function.Consumer<String> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUserDataListener(java.util.function.Consumer<String> listener) {
        listeners.remove(listener);
    }


    private void performLogout() {
        if (myName != null && client != null) {
            try {
                // Отправляем LOGOUT
                sendToServer("LOGOUT:" + myName);
                // Ждём немного
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                // Закрываем соединение
                client.stop();
            }
        }
    }


    // === Генерация уникального цвета для каждого пользователя ===
    // === Генерация уникального цвета для каждого пользователя ===
    private Color getUserColor(String username) {
        return userColors.computeIfAbsent(username, k -> {
            // Используем хеш-код имени как основу, но добавляем "шум" для разнообразия
            int seed = k.hashCode() ^ System.identityHashCode(k); // XOR с identity hash для большей случайности

            Random random = new Random(seed);

            // Генерируем яркие, насыщенные цвета (не слишком тёмные, не слишком светлые)
            int r = random.nextInt(200) + 55;   // 55–254
            int g = random.nextInt(200) + 55;   // 55–254
            int b = random.nextInt(200) + 55;   // 55–254

            // Исключаем очень светлые цвета (близкие к белому) — они плохо читаются на белом фоне
            while (r > 230 && g > 230 && b > 230) {
                r = random.nextInt(200) + 55;
                g = random.nextInt(200) + 55;
                b = random.nextInt(200) + 55;
            }

            return new Color(r, g, b);
        });
    }

}