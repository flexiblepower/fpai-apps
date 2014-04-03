package nl.tno.fpai.demo.scenario.data;

public class IdSet {
    private final String name;
    private final int count;

    public IdSet(String name, int count) {
        this.name = name;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "<idSet name=\"" + name + "\" count=\"" + count + "\"/>";
    }
}
