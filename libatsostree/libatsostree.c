#include <jni.h>
#include <string.h>
#include "com_advancedtelematic_libostree_LibOstree.h"
#include <gio/gio.h>
#include <ostree.h>
#include <jni.h>
#include <stdio.h>

jint throwLibOstreeException(JNIEnv *env, char *message)
{
  jclass exClass;
  char *className = "com/advancedtelematic/libostree/LibOstreeException";
  exClass = (*env)->FindClass(env, className);
  return (*env)->ThrowNew(env, exClass, message);
}

JNIEXPORT jstring JNICALL Java_com_advancedtelematic_libostree_LibOstree_parentOf(JNIEnv *env, jobject obj, jstring jrepoPath, jstring jcommitHash) {

  GError *error = NULL;
  gboolean suc = FALSE;
  g_autofree char *parent = NULL;

  const char *commitHash = (*env)->GetStringUTFChars(env, jcommitHash, 0);
  const char *repoPath = (*env)->GetStringUTFChars(env, jrepoPath, 0);

  GFile* file =  g_file_new_for_path(repoPath);
  OstreeRepo* repo = ostree_repo_new(file);

  g_autoptr(GVariant) commit = NULL;

  suc = ostree_repo_open(repo, NULL, &error);

  if(error != NULL) {
    throwLibOstreeException(env, error->message);
    return NULL;
  }

  suc = ostree_repo_load_variant_if_exists(repo,
                                           OSTREE_OBJECT_TYPE_COMMIT,
                                           commitHash,
                                           &commit,
                                           &error);

  (*env)->ReleaseStringUTFChars(env, jcommitHash, commitHash);
  (*env)->ReleaseStringUTFChars(env, jrepoPath, repoPath);

  if(error != NULL) {
    throwLibOstreeException(env, error->message);
    return NULL;
  }

  if(!suc || !commit) {
    return NULL;
  }

  parent = ostree_commit_get_parent(commit);

  return (*env)->NewStringUTF(env, parent);
}
