#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QProcess>
#include <QTimer>
#include <QRegularExpression>
#include "vendor/qrcodegen.hpp"
#include <exception>
class Pairing:public QObject {
 Q_OBJECT
 Q_PROPERTY(QStringList rows READ rows NOTIFY changed)
 Q_PROPERTY(QString status READ status NOTIFY changed)
 Q_PROPERTY(bool busy READ busy NOTIFY changed)
public:
 Pairing(){timer.setSingleShot(true);timer.setInterval(60000);connect(&timer,&QTimer::timeout,this,[this]{if(proc.state()!=QProcess::NotRunning){proc.kill();fail(tr("Pairing timed out."));}});connect(&proc,&QProcess::finished,&timer,&QTimer::stop);connect(&proc,&QProcess::finished,this,[this](int code,QProcess::ExitStatus state){
   QString output=QString::fromUtf8(proc.readAllStandardOutput()); output.remove(QRegularExpression("\\x1b\\[[0-9;?]*[A-Za-z]"));
   auto m=QRegularExpression("https://[^\\s\\x1b]+/auth/connect/[A-Za-z0-9_-]+").match(output);
   if(code || state!=QProcess::NormalExit || !m.hasMatch() || m.capturedLength()>4096){fail(tr("Could not generate a code. Check OpenCode and your connection."));return;}
   QByteArray link=m.captured().toUtf8();
   try {
    auto qr=qrcodegen::QrCode::encodeText(link.constData(),qrcodegen::QrCode::Ecc::MEDIUM);link.fill('\0');
    int w=qr.getSize();QString border(w+8,'0');for(int i=0;i<4;i++)data<<border;
    for(int y=0;y<w;y++){QString row(4,'0');for(int x=0;x<w;x++)row+=qr.getModule(x,y)?'1':'0';data<<row+QString(4,'0');}for(int i=0;i<4;i++)data<<border;
   } catch(const std::exception &) {link.fill('\0');fail(tr("QR generation failed."));return;}
   message=tr("Scan the code in the phone app. It expires in five minutes.");working=false;emit changed();
 });connect(&proc,&QProcess::errorOccurred,this,[this](QProcess::ProcessError e){if(e==QProcess::FailedToStart)fail(tr("OpenCode was not found. Install it and add it to PATH."));});}
 QStringList rows()const{return data;} QString status()const{return message;} bool busy()const{return working;}
 Q_INVOKABLE void generate(){if(working)return;data.clear();message=tr("Generating…");working=true;emit changed();
#ifdef Q_OS_WIN
 proc.start("cmd.exe",{"/d","/s","/c","opencode pair --remote --yes"});
#else
 proc.start("opencode",{"pair","--remote","--yes"});
#endif
 timer.start();
 }
signals:
 void changed();
private:void fail(const QString &s){data.clear();message=s;working=false;emit changed();} QProcess proc;QTimer timer;QStringList data;QString message=tr("Generate a code to connect your phone.");bool working=false;
};
int main(int argc,char**argv){QGuiApplication app(argc,argv);app.setDesktopFileName("phonepair");Pairing p;
 if(app.arguments().contains("--self-test")){
  QObject::connect(&p,&Pairing::changed,&app,[&]{if(!p.busy())app.exit(p.rows().size()>=20?0:1);});
  QTimer::singleShot(15000,&app,[&]{app.exit(2);});
  QTimer::singleShot(0,&p,[&]{p.generate();});
  return app.exec();
 }
 QQmlApplicationEngine engine;engine.rootContext()->setContextProperty("pairing",&p);engine.load(QUrl("qrc:/Main.qml"));if(engine.rootObjects().isEmpty())return 1;return app.exec();}
#include "main.moc"
