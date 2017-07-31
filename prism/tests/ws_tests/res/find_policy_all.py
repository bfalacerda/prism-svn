import os 
from subprocess import call 

prodloc = '.'; 
advloc = '../res/'; 
traext = '_ws_prod.tra'; 
staext = '_ws_prod.sta'; 
advext = '_adv.tra';
tarext = '_tar.lab';
ns = 1

def readprodstate(fn):
    f = open(fn,'r');
    f.readline()
    indToState={}
    for line in f:
        y = line.split(':')
        y[1] = y[1].strip('\n')
        indToState[y[0]]=y[1]
    f.close();
    return indToState;

def getAllfiles(loc,ext):
    fileslist = [x for x in os.listdir(loc) if x.endswith(ext)];
    return fileslist


def getfiles(prodloc,advloc,traext,staext,advext,tarext):
    trafiles = getAllfiles(prodloc,traext);
    stafiles = getAllfiles(prodloc,staext);
    advfiles = getAllfiles(advloc,advext);
    tarfiles = getAllfiles(advloc,tarext);
    modnames = []; 
    for fn in trafiles:
        temp = fn[1:len(fn)-len(traext)];
        modnames.append(temp); 
    return (modnames,trafiles,stafiles,advfiles,tarfiles)

def getinitstate(advloc,advfile):
    f=open(advloc+advfile);
    f.readline();
    line = f.readline();
    f.close();
    line = line.split(':');
    init_s = int(line[0]);
    return init_s;

def getfilelines(prodloc,stafn):
    if prodloc == '.':
        f = open(stafn,'r'); 
    else:
        f = open(prodloc+stafn,'r');
    f.readline(); 
    filelines = []; 
    for line in f:
#        temp = line.split(' '); 
        filelines.append(line);
    f.close();
    return filelines;

def count_policies(ind,pol_lines):
    nl = len(file_lines)
    nump = 0
    starter = [];
    for j in range(nl):
        line = pol_lines[j]
        temp = line.split(' ')
        temp0 = int(temp[0])
        if(temp0 == ind):
            nump= nump+1;
            temp1 = int(temp[1]);
            starter.append(temp1);
    return (nump,starter)


def get_policy(ind,pol_lines,ns):
    (nump,next_s) = count_policies(ind,pol_lines); 
    policy = [] 
    #next_s = []
    for i in range(nump):
        policy.append([ind,next_s[i]])
    #    next_s.append(ind)
    rep = True
    while(rep == True):
        rep = False
        for j in range(num_lines):
            line=file_lines[j];
            temp = line.split(' ');
            for i in range(nump):
                temp0 = int(temp[0]);
                if (temp0 == next_s[i]):
                    #print(str(next_s[i])+' to '+ temp[ns]);
                    next_s[i] = int(temp[ns]);
                    policy[i].append(next_s[i]);
                    if(temp0 > next_s[i]):
                        rep = True
                    #    print('repeat')
    return policy

def get_policy_adv(ind,pol_lines):
    return get_policy(ind,pol_lines,1);

def get_policy_tras(ind,pol_lines):
    return get_policy(ind,pol_lines,2);

def policy_pp(pol):
    for j in range(len(pol)):
        if (type(pol[j])==int):
            print str(pol[j])+",",
        else:
            print "\n\t",
            policy_pp(pol[j]);

def policy_pp_ind2state(pol,indtostate,f):
    writefn = (not (f is None));
    #if writefn:
     #   f = open(fn,'w');

    for j in range(len(pol)):
        if (type(pol[j])==int):
            print indtostate[str(pol[j])]+" ,",
            if writefn:
                f.write(indtostate[str(pol[j])]+" ,");
        else:
            print "\n\t",
            if writefn:
                f.write("\n\t")
            policy_pp_ind2state(pol[j],indtostate,f);
    #if writefn:
    #    f.close();

def get_file_with_name(name,fls):
    for fl in fls:
        #print fl
        #print name
        if (name+"_tar" in fl) or (name+"_ws" in fl) or (name+"_adv") in fl:
            #print fl;
            return fl;
    return '';

def fixstate(tofix):
    t0 = tofix
    t0 = t0.strip('()')
    t0 = t0.split(',')
    t0 = '_'.join(t0[1:len(t0)])
    t0 = t0.replace('-1','x')
    return t0

def get_tra_lines(file_lines):
    tra_lines=[];
    for line in file_lines:
        temp = (line.strip('\n')).split(' ')
        tra_line = [temp[0],temp[1],temp[2],''.join(temp[3:len(temp)])]
        tra_lines.append(tra_line)
    return tra_lines

def create_easytra(fn,tra_lines):
    f=open(fn,'w')
    f.write(str(len(tra_lines))+'\n')
    for tra_line in tra_lines:
        tra_line[0] = 's'+indToState[tra_line[0]].strip('()').replace('-1','x').replace(',','_')
        tra_line[1] = 's'+indToState[tra_line[1]].strip('()').replace('-1','x').replace(',','_')
        temp = ' '.join(tra_line)
        f.write(temp+'\n')
        #print tra_lines
    f.close()


def create_easytra_dot(fn,tra_lines):
    f=open(fn,'w')
    f.write('digraph adv {\nrankdir=LR\n')
    d=dict()
    for tra_line in tra_lines:
        towrite = tra_line[0]+' -> '+tra_line[1]+' [label="'+tra_line[3]+'('+tra_line[2]+')"];\n'
        f.write(towrite)
        if tra_line[0] in d:
            d[tra_line[0]] +=1
        else:
            d[tra_line[0]]=1
        if tra_line[1] in d:
            d[tra_line[1]] +=1
        else:
            d[tra_line[1]]=1
    f.write('}')
    f.close()


(names,tras,stas,advs,tars)=getfiles(prodloc,advloc,traext,staext,advext,tarext); 
#i = 0; 
#if i == 0:
for i in range(len(names)):
    tar = get_file_with_name(names[i],tars);
    sta = get_file_with_name(names[i],stas); 
    adv = get_file_with_name(names[i],advs);
    print ("Reading policy for "+names[i]+" from files\n\t"+tar+"\n\t"+sta+"\n\t"+adv);
    init_s = getinitstate(advloc,tar);
    model_name = names[i]; 
    indToState = readprodstate(sta); 
    policy = []; 
    next_s = []; 
    num_policices = 0; 
    file_lines = getfilelines(advloc,adv);
    tra_lines = get_tra_lines(file_lines);
    easytrafn = names[i]+"_adv_easy.tra";
    easyadvfn = names[i]+"_adv_easy.dot";
    create_easytra(easytrafn,tra_lines);
    create_easytra_dot(easyadvfn,tra_lines);
    if not "six" in names[i]:
        call("dot -Tpdf "+easyadvfn+" -o "+names[i]+"_adv_easy.pdf",shell=True)
    num_lines = len(file_lines);
    print ("Reading policy for "+names[i]+" from files\n\t"+tar+"\n\t"+sta+"\n\t"+adv);
    (num_policies,next_s) = count_policies(init_s,file_lines);
    #print ('done')
    policy = get_policy_adv(init_s,file_lines);

    toexp=[];
    for k in range(num_policies):
        toexp.append([]);
        for j in range(1,len(policy[k])):
            (nump,temp) = count_policies(policy[k][j],file_lines);
            if nump > 1:
                policy[k][j] = get_policy_adv(policy[k][j],file_lines);
                toexp[k].append(j)
    print("policy for "+names[i])
    policy_pp(policy)
    print("\neasy policy")
    f = open(names[i]+"easy_pol.txt",'w');
    policy_pp_ind2state(policy,indToState,f);
    f.close();
    raw_input()


    

    


