/* Signature canonique d'un treillis, invariante par renumerotation des concepts.
 *
 * Le tableau plat ne porte que les extents/intents REDUITS. On reconstruit
 * l'extent complet par extent(c) = rextent(c) U  U extent(d), d enfant de c,
 * en ordre topologique (enfants d'abord). Les extents caracterisent les
 * concepts, donc :
 *   signature = H( multiset des extents , multiset des couples d'extents relies )
 * Deux executions produisant le meme treillis ont la meme signature, quel que
 * soit l'ordre d'attribution des identifiants. */
#include <stdio.h>
#define NCASES 40
#include <stdlib.h>
#include <string.h>
#include "algo/latticecbo.h"
#include "core/conceptorder.h"

static unsigned long long rs;
static unsigned long long xs(void){ rs^=rs<<13; rs^=rs>>7; rs^=rs<<17; return rs; }

static unsigned long long fnv(const unsigned char *p, size_t n){
    unsigned long long h=1469598103934665603ULL;
    for(size_t i=0;i<n;i++){ h^=p[i]; h*=1099511628211ULL; }
    return h;
}
static int cmp_ull(const void*a,const void*b){
    unsigned long long x=*(const unsigned long long*)a,y=*(const unsigned long long*)b;
    return x<y?-1:(x>y?1:0);
}

static unsigned long long canon_signature(const int *flat, int nObj, int *outN, int *outE){
    int p=0; int N=flat[p++]; int E=flat[p++];
    *outN=N; *outE=E;
    int *ec=(int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    int *ep=(int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    for(int i=0;i<E;i++){ ec[i]=flat[p++]; ep[i]=flat[p++]; }

    unsigned char *ext=(unsigned char*)calloc((size_t)N*(size_t)nObj,1);
    int *nchild=(int*)calloc((size_t)N,sizeof(int));
    for(int i=0;i<E;i++) nchild[ep[i]]++;
    /* CSR enfants */
    int *cptr=(int*)calloc((size_t)N+1,sizeof(int));
    for(int i=0;i<E;i++) cptr[ep[i]+1]++;
    for(int c=0;c<N;c++) cptr[c+1]+=cptr[c];
    int *cadj=(int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    { int *cur=(int*)malloc((size_t)(N+1)*sizeof(int));
      memcpy(cur,cptr,(size_t)(N+1)*sizeof(int));
      for(int i=0;i<E;i++) cadj[cur[ep[i]]++]=ec[i];
      free(cur); }

    /* rextents depuis le flat */
    for(int c=0;c<N;c++){
        int k=flat[p++];
        for(int j=0;j<k;j++){ int o=flat[p++]; ext[(size_t)c*nObj+o]=1; }
        int k2=flat[p++]; p+=k2;   /* rintent ignore ici */
    }

    /* ordre topologique : un concept est pret quand tous ses enfants le sont */
    int *pending=(int*)malloc((size_t)N*sizeof(int));
    memcpy(pending,nchild,(size_t)N*sizeof(int));
    int *stack=(int*)malloc((size_t)N*sizeof(int)); int sp=0;
    for(int c=0;c<N;c++) if(pending[c]==0) stack[sp++]=c;
    /* parents d'un concept */
    int *pptr=(int*)calloc((size_t)N+1,sizeof(int));
    for(int i=0;i<E;i++) pptr[ec[i]+1]++;
    for(int c=0;c<N;c++) pptr[c+1]+=pptr[c];
    int *padj=(int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    { int *cur=(int*)malloc((size_t)(N+1)*sizeof(int));
      memcpy(cur,pptr,(size_t)(N+1)*sizeof(int));
      for(int i=0;i<E;i++) padj[cur[ec[i]]++]=ep[i];
      free(cur); }

    int done=0;
    while(sp>0){
        int c=stack[--sp]; done++;
        unsigned char *dst=ext+(size_t)c*nObj;
        for(int k=cptr[c];k<cptr[c+1];k++){
            const unsigned char *src=ext+(size_t)cadj[k]*nObj;
            for(int o=0;o<nObj;o++) dst[o]|=src[o];
        }
        for(int k=pptr[c];k<pptr[c+1];k++)
            if(--pending[padj[k]]==0) stack[sp++]=padj[k];
    }
    if(done!=N){ fprintf(stderr,"CYCLE ou graphe incomplet: %d/%d\n",done,N); }

    unsigned long long *h=(unsigned long long*)malloc((size_t)N*sizeof(unsigned long long));
    for(int c=0;c<N;c++) h[c]=fnv(ext+(size_t)c*nObj,(size_t)nObj);

    unsigned long long *pairs=(unsigned long long*)malloc((size_t)(E>0?E:1)*sizeof(unsigned long long));
    for(int i=0;i<E;i++) pairs[i]=h[ec[i]]*1000003ULL ^ (h[ep[i]]+0x9e3779b97f4a7c15ULL);

    unsigned long long *hs=(unsigned long long*)malloc((size_t)N*sizeof(unsigned long long));
    memcpy(hs,h,(size_t)N*sizeof(unsigned long long));
    qsort(hs,(size_t)N,sizeof(unsigned long long),cmp_ull);
    qsort(pairs,(size_t)(E>0?E:1),sizeof(unsigned long long),cmp_ull);

    unsigned long long sig=1469598103934665603ULL;
    for(int c=0;c<N;c++){ sig^=hs[c]; sig*=1099511628211ULL; }
    for(int i=0;i<E;i++){ sig^=pairs[i]; sig*=1099511628211ULL; }

    free(ec);free(ep);free(ext);free(nchild);free(cptr);free(cadj);
    free(pending);free(stack);free(pptr);free(padj);free(h);free(pairs);free(hs);
    return sig;
}


/* ── Harnais ────────────────────────────────────────────────────────────────
 *
 * Usage :
 *   latticecbo_regress                 génère les signatures et les affiche
 *   latticecbo_regress ref.txt         compare à un fichier de référence
 *
 * Pour créer la référence après un changement volontaire du treillis :
 *   latticecbo_regress > ref.txt
 *
 * Le nombre de threads est balayé (1, 4, 16) : une signature qui dépend du
 * nombre de threads signale une course ou une dépendance à l'ordonnancement.
 * ─────────────────────────────────────────────────────────────────────────── */
static int gen_cases(FILE *out){
    static const int THREADS[] = {1, 4, 16};
    char buf[256];
    for(unsigned ti=0; ti<sizeof(THREADS)/sizeof(THREADS[0]); ti++){
        snprintf(buf,sizeof(buf),"%d",THREADS[ti]);
#ifdef _WIN32
        _putenv_s("FCA4J_THREADS", buf);
#else
        setenv("FCA4J_THREADS", buf, 1);
#endif
        for(int t=0;t<NCASES;t++){
            rs = 1ULL + (unsigned long long)t*2654435761ULL;
            int no = 40 + (int)(xs()%200), na = 5 + (int)(xs()%12);
            double d = 0.10 + (double)(xs()%45)/100.0;
            BinaryContext *c = ctx_create(no,na,"t");
            for(int o=0;o<no;o++) for(int a=0;a<na;a++)
                if((double)(xs()%100000)/100000.0 < d) ctx_set(c,o,a,true);
            int l=0; int *f = run_latticecbo_csr_flat(c,&l);
            int N=0,E=0;
            unsigned long long sig = canon_signature(f,no,&N,&E);
            fprintf(out,"th=%d t=%d %dx%d N=%d E=%d canon=%016llx\n",
                    THREADS[ti],t,no,na,N,E,sig);
            free(f); ctx_free(c);
        }
    }
    return 0;
}

int main(int argc, char **argv){
    if(argc < 2){ gen_cases(stdout); return 0; }

    FILE *ref = fopen(argv[1],"r");
    if(!ref){ fprintf(stderr,"reference illisible : %s\n", argv[1]); return 2; }
    char tmpname[] = "regress_out.txt";
    FILE *cur = fopen(tmpname,"w+");
    if(!cur){ fprintf(stderr,"ecriture impossible\n"); fclose(ref); return 2; }
    gen_cases(cur);
    rewind(cur);

    char a[512], b[512];
    int line=0, bad=0;
    for(;;){
        char *ra = fgets(a,sizeof(a),ref);
        char *rb = fgets(b,sizeof(b),cur);
        if(!ra && !rb) break;
        line++;
        if(!ra || !rb){ printf("ECART ligne %d : nombre de cas different\n",line); bad++; break; }
        if(strcmp(a,b)!=0){
            printf("ECART ligne %d\n  attendu : %s  obtenu  : %s",line,a,b);
            bad++;
        }
    }
    fclose(ref); fclose(cur); remove(tmpname);
    if(bad==0){ printf("OK : %d cas, treillis inchange\n",line); return 0; }
    printf("ECHEC : %d ecart(s) sur %d cas\n",bad,line);
    return 1;
}
