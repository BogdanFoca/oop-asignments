package simulation;

import database.Database;
import entities.Child;
import entities.Gift;
import enums.Category;
import enums.ChildCategory;
import utils.AnnualChange;
import utils.Comparers;
import utils.JSONOutput;
import utils.JSONReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SimulationManager {
    static SimulationManager instance;

    private final Map<Child, Double> budgetByChild = new HashMap<Child, Double>();
    JSONOutput jsonOutput;

    public static SimulationManager getInstance() {
        if(instance == null) {
            instance = new SimulationManager();
        }
        return instance;
    }

    public Map<Child, Double> getBudgetByChild() {
        return budgetByChild;
    }

    public JSONOutput startSimulation(JSONReader jsonReader) {
        jsonOutput = new JSONOutput();
        budgetByChild.clear();
        round0();
        for (int i = 0; i < jsonReader.getNumberOfYears(); i++) {
            roundYear(jsonReader.getAnnualChanges().get(i), i + 1);
        }
        return jsonOutput;
    }

    void round0() {
        ArrayList<JSONOutput.OutputChild> outputChildren = new ArrayList<JSONOutput.OutputChild>();
        updateBudgets();
        for (Child c : Database.getInstance().getChildren()) {
            outputChildren.add(new JSONOutput().new OutputChild(
                    c.getId(),
                    c.getLastName(),
                    c.getFirstName(),
                    c.getCity(),
                    c.getAge(),
                    c.getGiftPreference(),
                    c.getNiceScores().get(0),
                    budgetByChild.get(c)
            ));
        }
        giveGifts(outputChildren);
        jsonOutput.addOutputChildren(outputChildren);
    }

    void roundYear(AnnualChange annualChange, int year) {
        ArrayList<JSONOutput.OutputChild> outputChildren = new ArrayList<JSONOutput.OutputChild>();
        updateData(annualChange);
        updateBudgets();
        for (Child c : Database.getInstance().getChildren()) {
            outputChildren.add(new JSONOutput().new OutputChild(
                    c.getId(),
                    c.getLastName(),
                    c.getFirstName(),
                    c.getCity(),
                    c.getAge(),
                    c.getGiftPreference(),
                    c.getNiceScores().get(0),
                    budgetByChild.get(c)
            ));
        }
        giveGifts(outputChildren);
        jsonOutput.addOutputChildren(outputChildren);
    }

    Gift getAppropriateGift(double budget, Category category) {
        List<Gift> giftsInCategory = new ArrayList<Gift>(Database.getInstance().getGifts());
        giftsInCategory = giftsInCategory.stream().filter(g -> g.getCategory().equals(category)).collect(Collectors.toList());
        giftsInCategory.sort(new Comparers.CompareGiftsByPrice());
        if (giftsInCategory.size() == 0 || giftsInCategory.get(0).getPrice() > budget) {
            return null;
        }
        return giftsInCategory.get(0);
    }

    void updateBudgets() {
        List<Child> children = Database.getInstance().getChildren();
        double averageScore = 0;
        for (Child c : children) {
            c.setChildCategory();
            c.setAverageNiceScore();
            averageScore += c.getAverageNiceScore();
        }
        averageScore /= Database.getInstance().getChildren().size();
        double budgetUnit = Database.getInstance().getSantaBudget() / averageScore;
        for (Child c: children) {
            budgetByChild.put(c, c.getAverageNiceScore() * budgetUnit);
        }
    }

    void giveGifts(List<JSONOutput.OutputChild> outputChildren) {
        List<Child> children = Database.getInstance().getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).getAverageNiceScore() >= 0) {
                for (Category cat : children.get(i).getGiftPreference()) {
                    Gift gift = getAppropriateGift(budgetByChild.get(children.get(i)), cat);
                    if (gift != null) {
                        children.get(i).receiveGift(gift);
                        outputChildren.get(i).addGift(gift);
                        budgetByChild.put(children.get(i), budgetByChild.get(children.get(i)) - gift.getPrice());
                        Database.getInstance().getGifts().remove(gift);
                    }
                }
            }
        }
    }

    void updateData(AnnualChange annualChange) {
        List<Child> children = Database.getInstance().getChildren();
        for (Child c : children) {
            c.incrementAge();
            c.setChildCategory();
            if (c.getChildCategory() == ChildCategory.Young_Adult) {
                children.remove(c);
            }
        }
        for (Child c : annualChange.getNewChildren()) {
            c.setChildCategory();
            if(c.getChildCategory() != ChildCategory.Young_Adult) {
                children.add(c);
            }
        }
        for (AnnualChange.ChildUpdate cu : annualChange.getChildrenUpdates()) {
            Child child = Database.getInstance().getChildren().stream().filter(c -> c.getId() == cu.getId()).findFirst().orElse(null);
            if (child != null) {
                if(cu.getNiceScore() != null) {
                    child.addNiceScore(cu.getNiceScore());
                }
            }
            for (Category c : cu.getNewPreferences()) {
                child.addNewPreference(c);
            }
        }
        Database.getInstance().getGifts().addAll(annualChange.getNewGifts());
    }
}
