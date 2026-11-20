#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>

JNIEXPORT jintArray JNICALL Java_com_osean_opencodephone_PtyNative_spawn(JNIEnv *env,jclass cls,jstring directory){
    (void)cls; const char *cwd=(*env)->GetStringUTFChars(env,directory,0);if(!cwd)return NULL;
    int master=posix_openpt(O_RDWR|O_NOCTTY|O_CLOEXEC);if(master<0||grantpt(master)||unlockpt(master)){if(master>=0)close(master);(*env)->ReleaseStringUTFChars(env,directory,cwd);return NULL;}
    char *slave=ptsname(master);if(!slave){close(master);(*env)->ReleaseStringUTFChars(env,directory,cwd);return NULL;}
    pid_t pid=fork();
    if(pid==0){
        setsid();int fd=open(slave,O_RDWR);if(fd<0)_exit(127);ioctl(fd,TIOCSCTTY,0);dup2(fd,0);dup2(fd,1);dup2(fd,2);if(fd>2)close(fd);close(master);chdir(cwd);setenv("TERM","xterm-256color",1);execl("/system/bin/sh","sh","-i",(char *)NULL);_exit(127);
    }
    (*env)->ReleaseStringUTFChars(env,directory,cwd);if(pid<0){close(master);return NULL;}
    jint values[2]={master,(jint)pid};jintArray result=(*env)->NewIntArray(env,2);if(!result){close(master);kill(pid,SIGHUP);return NULL;}(*env)->SetIntArrayRegion(env,result,0,2,values);return result;
}
JNIEXPORT jint JNICALL Java_com_osean_opencodephone_PtyNative_read(JNIEnv *env,jclass cls,jint fd,jbyteArray buffer){
    (void)cls;jsize cap=(*env)->GetArrayLength(env,buffer);jbyte *bytes=(*env)->GetByteArrayElements(env,buffer,0);if(!bytes)return -1;ssize_t n;do{n=read(fd,bytes,(size_t)cap);}while(n<0&&errno==EINTR);(*env)->ReleaseByteArrayElements(env,buffer,bytes,n>0?0:JNI_ABORT);return (jint)n;
}
JNIEXPORT jint JNICALL Java_com_osean_opencodephone_PtyNative_write(JNIEnv *env,jclass cls,jint fd,jbyteArray buffer){
    (void)cls;jsize len=(*env)->GetArrayLength(env,buffer);jbyte *bytes=(*env)->GetByteArrayElements(env,buffer,0);if(!bytes)return -1;ssize_t off=0;while(off<len){ssize_t n=write(fd,bytes+off,(size_t)(len-off));if(n<0&&errno==EINTR)continue;if(n<=0)break;off+=n;}(*env)->ReleaseByteArrayElements(env,buffer,bytes,JNI_ABORT);return (jint)off;
}
JNIEXPORT void JNICALL Java_com_osean_opencodephone_PtyNative_resize(JNIEnv *env,jclass cls,jint fd,jint cols,jint rows){
    (void)env;(void)cls;if(cols<1||rows<1||cols>1000||rows>1000)return;struct winsize size={.ws_row=(unsigned short)rows,.ws_col=(unsigned short)cols};ioctl(fd,TIOCSWINSZ,&size);
}
JNIEXPORT void JNICALL Java_com_osean_opencodephone_PtyNative_close(JNIEnv *env,jclass cls,jint fd,jint pid){
    (void)env;(void)cls;if(pid>0)kill(pid,SIGHUP);if(fd>=0)close(fd);if(pid>0)waitpid(pid,NULL,WNOHANG);
}
