import java.util.BitSet;

/**
 * @author gaoshuo
 * @date 2019-10-11
 */
public class Bloom {
    private static final int SIZE = 1<<24;
    BitSet bitSet=new BitSet(SIZE);
    Hash[] hashs=new Hash[8];
    private static final int seeds[]=new int[]{3,5,7,9,11,13,17,19};

    public Bloom(){
        for (int i = 0; i < seeds.length; i++) {
            hashs[i]=new Hash(seeds[i]);
        }
    }
    public void add(String string){
        for(Hash hash:hashs){
            bitSet.set(hash.getHash(string),true);
        }
    }
    public boolean contains(String string){
        boolean have=true;
        for(Hash hash:hashs){
            have&=bitSet.get(hash.getHash(string));
        }
        return have;
    }
    class Hash{
        private int seed = 0;
        public Hash(int seed){
            this.seed=seed;
        }
        public int getHash(String string){
            int val=0;
            int len=string.length();
            for (int i = 0; i < len; i++) {
                val=val*seed+string.charAt(i);
            }
            return val&(SIZE-1);
        }
    }
}
