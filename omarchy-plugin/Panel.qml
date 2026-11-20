import QtQuick
import QtQuick.Controls as Controls
import Quickshell
import Quickshell.Io
import qs.Commons
import qs.Commons as Commons
import qs.Ui

Panel {
  id: root
  moduleName: "io.github.kkaattssuu.opencode-phone"
  ipcTarget: "io.github.kkaattssuu.opencode-phone"
  implicitWidth: Style.bar.iconSlot
  implicitHeight: Style.bar.iconSlot
  property var rows: []
  property bool failed: false
  property bool cancelled: false
  property int autoState: -1
  property bool autoError: false
  Commons.Translations { id: strings; directory: Qt.resolvedUrl("i18n") }
  onOpenedChanged: {
    if (opened) { refreshAutostart("status") }
    else { cancelled = true; if (generator.running) generator.running = false; rows = []; failed = false }
  }
  function generate() {
    if (generator.running) return
    rows = []; failed = false; cancelled = false
    generator.running = true
  }
  function refreshAutostart(action) {
    if (autostart.running) return
    autoError = false
    autostart.command = ["python3", Qt.resolvedUrl("autostart.py").toString().replace("file://", ""), action]
    autostart.running = true
  }
  Process {
    id: autostart
    stdout: StdioCollector { onStreamFinished: {
      let state = text.trim()
      if (state === "enabled") root.autoState = 1
      else if (state === "disabled") root.autoState = 0
      else root.autoError = true
    }}
    onExited: function(code) { if (code !== 0) root.autoError = true }
  }
  Process {
    id: generator
    command: ["python3", Qt.resolvedUrl("qr.py").toString().replace("file://", "")]
    stdout: StdioCollector { onStreamFinished: {
      if (root.cancelled || !root.opened) return
      let lines = text.split("\n")
      if (lines.length < 20 || lines.length > 200 || !lines.every(l => l.length === lines.length && /^[01]+$/.test(l))) { root.failed = true; return }
      root.rows = lines
    }}
    onExited: function(code) { if (!root.cancelled && root.opened && code !== 0) root.failed = true }
  }
  BarIconButton {
    id: button
    anchors.fill: parent
    bar: root.bar
    text: "󰄀"
    tooltipText: ""
    onPressed: function(b) { root.toggle() }
  }
  KeyboardPanel {
    id: panel
    anchorItem: button
    owner: root
    bar: root.bar
    open: root.opened
    focusTarget: panel.loadedContent
    contentWidth: panel.fittedContentWidth(Style.space(320))
    contentHeight: panel.fittedContentHeight(panel.loadedContentHeight)
    content: PanelKeyCatcher {
      anchors.fill: parent
      implicitHeight: column.implicitHeight
      onCloseRequested: root.close()
      onTabRequested: function(direction) { root.switchPanel(direction) }
      Column {
        id: column
        width: parent.width
        spacing: Style.space(14)
        Text { textFormat: Text.PlainText; text: strings.t("title"); color: "#eff8ff"; font.pixelSize: Style.font.body; font.bold: true }
        Text { visible: generator.running; textFormat: Text.PlainText; text: strings.t("loading"); color: "#eff8ff" }
        Text { visible: root.failed; textFormat: Text.PlainText; text: strings.t("error"); color: "#ff6688"; width: parent.width; wrapMode: Text.Wrap }
        Rectangle {
          id: canvas
          visible: root.rows.length > 0
          readonly property int cell: root.rows.length ? Math.max(2, Math.floor(Style.space(256) / root.rows.length)) : 0
          width: root.rows.length * cell; height: width; anchors.horizontalCenter: parent.horizontalCenter; color: "white"
          Grid {
            anchors.fill: parent; columns: root.rows.length; LayoutMirroring.enabled: false
            Repeater {
              model: root.rows.length * root.rows.length
              Rectangle { required property int index; width: canvas.cell; height: canvas.cell; color: root.rows[Math.floor(index / root.rows.length)].charAt(index % root.rows.length) === "1" ? "black" : "white" }
            }
          }
        }
        Text { visible: root.rows.length > 0; textFormat: Text.PlainText; text: strings.t("hint"); color: "#c0aeb6"; width: parent.width; wrapMode: Text.Wrap }
        Row {
          width: parent.width
          spacing: Style.space(8)
          Text {
            anchors.verticalCenter: parent.verticalCenter
            width: parent.width - autoSwitch.width - parent.spacing
            textFormat: Text.PlainText; text: strings.t("autostart"); color: "#eff8ff"; font.bold: true; wrapMode: Text.Wrap
          }
          Controls.Switch {
            id: autoSwitch
            enabled: root.autoState !== -1 && !autostart.running
            checked: root.autoState === 1
            indicator: Rectangle {
              implicitWidth: Style.space(52)
              implicitHeight: Style.space(30)
              radius: height / 2
              color: autoSwitch.checked ? "#bbef1f" : "#ff0055"
              opacity: autoSwitch.enabled ? 1 : 0.55
              Rectangle {
                width: parent.height - Style.space(6)
                height: width
                radius: width / 2
                x: Style.space(3) + autoSwitch.visualPosition * (parent.width - width - Style.space(6))
                anchors.verticalCenter: parent.verticalCenter
                color: "#eff8ff"
                Behavior on x { NumberAnimation { duration: Style.duration(160); easing.type: Easing.OutCubic } }
              }
              Behavior on color { ColorAnimation { duration: Style.duration(160) } }
            }
            onClicked: root.refreshAutostart(root.autoState === 1 ? "off" : "on")
          }
        }
        Text { textFormat: Text.PlainText; text: strings.t("autostartInfo"); color: "#c0aeb6"; width: parent.width; wrapMode: Text.Wrap }
        Text { visible: root.autoError; textFormat: Text.PlainText; text: strings.t("autostartError"); color: "#ff6688"; width: parent.width; wrapMode: Text.Wrap }
        Text { textFormat: Text.PlainText; text: strings.t("remoteWarning"); color: "#c0aeb6"; width: parent.width; wrapMode: Text.Wrap }
        Rectangle {
          width: parent.width; height: Style.space(42); radius: Style.space(10); color: "#ff0055"; opacity: generator.running ? 0.55 : 1
          Text { anchors.centerIn: parent; textFormat: Text.PlainText; text: strings.t("regenerate"); color: "#eff8ff"; font.bold: true }
          MouseArea { anchors.fill: parent; enabled: !generator.running; cursorShape: Qt.PointingHandCursor; onClicked: root.generate() }
        }
      }
    }
  }
}
