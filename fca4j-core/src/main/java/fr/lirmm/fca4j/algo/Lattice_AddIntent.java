package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

public class Lattice_AddIntent implements AbstractAlgo<ConceptOrder>
{

    private IBinaryContext context;
    private ConceptOrder order;
    private ISetFactory factory;
    private Chrono chrono;

    public Lattice_AddIntent(IBinaryContext context, Chrono chrono)
    {
        this.context = context;
        this.factory = context.getFactory();
        this.chrono = chrono;
    }

    public Lattice_AddIntent(IBinaryContext context)
    {
        this(context, null);
    }

    // =====================================================
    // ADD INTENT (dual de addExtent)
    // =====================================================

    protected int addIntent(ISet intent, int generator)
            throws Exception
    {
        generator =
                getSmallestContainingConcept(intent, generator);

        if (intent.equals(order.getConceptIntent(generator)))
        {
            return generator;
        }

        ArrayList<Integer> newParents =
                new ArrayList<>();

        Iterator<Integer> it =
                order.getUpperCoverIterator(generator);

        while (it.hasNext())
        {
            int parent = it.next();

            if (!order.getConceptIntent(parent)
                    .containsAll(intent))
            {
                ISet intersection =
                        intent.newIntersect(
                                order.getConceptIntent(parent));

                parent =
                        addIntent(intersection, parent);
            }

            boolean add = true;

            ArrayList<Integer> toRemove =
                    new ArrayList<>();

            for (int existing : newParents)
            {
                if (order.getConceptIntent(existing)
                        .containsAll(
                                order.getConceptIntent(parent)))
                {
                    add = false;
                    break;
                }

                if (order.getConceptIntent(parent)
                        .containsAll(
                                order.getConceptIntent(existing)))
                {
                    toRemove.add(existing);
                }
            }

            newParents.removeAll(toRemove);

            if (add)
                newParents.add(parent);
        }

        int newConcept =
                order.addConcept(
                        factory.clone(
                                order.getConceptExtent(generator)),
                        factory.clone(intent));

        for (int parent : newParents)
        {
            order.removePrecedenceConnection(
                    generator,
                    parent);

            order.addPrecedenceConnection(
                    newConcept,
                    parent);
        }

        order.addPrecedenceConnection(
                generator,
                newConcept);

        return newConcept;
    }

    // =====================================================

    private int getSmallestContainingConcept(
            ISet intent,
            int generator)
    {
        boolean moved = true;

        while (moved)
        {
            moved = false;

            for (int parent :
                    order.getUpperCoverSet(generator))
            {
                if (order.getConceptIntent(parent)
                        .containsAll(intent))
                {
                    generator = parent;
                    moved = true;
                    break;
                }
            }
        }

        return generator;
    }

    // =====================================================

    @Override
    public void run()
    {
        try
        {
            order =
                    new ConceptOrder(
                            "LatticeWithAddIntent",
                            context,
                            getDescription());

            // Bottom concept
            ISet allAttributes =
                    factory.createSet(context.getAttributeCount());

            allAttributes.fill(context.getAttributeCount());

            int bottom =
                    order.addConcept(
                            factory.createSet(),
                            allAttributes);

            // Insert intents of objects
            for (int obj = 0;
                 obj < context.getObjectCount();
                 obj++)
            {
                ISet intent =
                        factory.clone(
                                context.getIntent(obj));

                int concept =
                        addIntent(intent, bottom);

                order.getConceptReducedExtent(concept)
                        .add(obj);
            }

            computeReducedIntents();

            order.computeExtents();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    // =====================================================

    private void computeReducedIntents()
    {
        Iterator<Integer> it =
                order.getBasicIterator();

        while (it.hasNext())
        {
            int concept = it.next();

            ISet reduced =
                    factory.clone(
                            order.getConceptIntent(concept));

            for (int parent :
                    order.getUpperCoverSet(concept))
            {
                reduced.removeAll(
                        order.getConceptIntent(parent));
            }

            order.getConceptReducedIntent(concept)
                    .addAll(reduced);
        }
    }

    // =====================================================

    @Override
    public ConceptOrder getResult()
    {
        return order;
    }

    @Override
    public String getDescription()
    {
        return "AddIntent";
    }

}
