import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Controls.Material
ApplicationWindow {
 width:420;height:600;minimumWidth:340;minimumHeight:500;visible:true;color:"#0d0303";title:qsTr("OpenCode Phone Pairing");Material.theme:Material.Dark;Material.accent:"#bbef1f"
 ColumnLayout { anchors.fill:parent;anchors.margins:28;spacing:18
  Label{text:qsTr("CONNECT YOUR PHONE");color:"#bbef1f";font.bold:true}
  Label{text:qsTr("Your workspace,\nwithin reach.");color:"#eff8ff";font.pixelSize:28;font.bold:true}
  Item {Layout.fillWidth:true;Layout.fillHeight:true
   Rectangle {anchors.centerIn:parent;width:Math.min(parent.width,parent.height,290);height:width;color:"white";visible:pairing.rows.length>0
    Canvas{id:canvas;anchors.fill:parent;onPaint:{let c=getContext('2d');c.reset();let a=pairing.rows,n=a.length;if(!n)return;let z=width/n;c.fillStyle='black';for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(a[y][x]==='1')c.fillRect(Math.floor(x*z),Math.floor(y*z),Math.ceil((x+1)*z)-Math.floor(x*z),Math.ceil((y+1)*z)-Math.floor(y*z))}
     Connections{target:pairing;function onChanged(){canvas.requestPaint()}}
    }
   }
   Label{anchors.centerIn:parent;visible:pairing.rows.length===0;text:pairing.busy?qsTr("Generating…"):qsTr("Your QR code will appear here");color:"#c0aeb6"}
  }
  Label{Layout.fillWidth:true;text:qsTr("Generating a code enables internet access to your OpenCode server.");wrapMode:Text.Wrap;color:"#c0aeb6"}
  Label{Layout.fillWidth:true;text:pairing.status;wrapMode:Text.Wrap;color:"#c0aeb6"}
  Button{Layout.fillWidth:true;text:qsTr("Generate QR code");highlighted:true;enabled:!pairing.busy;onClicked:pairing.generate()}
 }
}
