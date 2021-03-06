package Logic;

import Annotations.CardName;
import Annotations.SelectAction;
import Data.DataManager;
import Exceptions.GameOverException;
import Exceptions.SelectionNeededException;
import Exceptions.InvalidChoiceException;
import Interfaces.*;
import Models.Cards.*;
import Models.Character;
import Models.Hero;
import Models.InfoPack;
import Models.Passive;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class Actions {
    private Game game;
    private HashMap<CardName, Method> methodMap = new HashMap<>();
    private HashMap<SelectAction, Method> selectActionMethodMap = new HashMap<>();
    private ArrayList<ActionHandler> attackActionHandlers = new ArrayList<>();

    public Actions(Game game){
        this.game = game;
        initialize();
    }

    private void initialize() {
        for(Method method: this.getClass().getDeclaredMethods()){
            for(Annotation annotation: method.getDeclaredAnnotations()){
                if(annotation instanceof CardName){
                    methodMap.put((CardName) annotation, method);
                }
                if(annotation instanceof SelectAction){
                    selectActionMethodMap.put((SelectAction) annotation, method);
                }
            }
        }
        addStealthCheck();
    }

    private void addStealthCheck() {
        game.getActionRequest().getPerformAction().addBeforeAction(new PerformActionHandler() {
            @Override
            public void runAction(InfoPack[] infoPacks) throws InvalidChoiceException {
                try{
                    if(infoPacks[1].getCharacter() instanceof Minion && infoPacks[0].getSide() != infoPacks[1].getSide() && ((Minion) infoPacks[1].getCharacter()).isStealth()){
                        throw new InvalidChoiceException();
                    }
                }catch (ArrayIndexOutOfBoundsException e){}
            }

            @Override
            public void runAction() throws Exception {

            }
        });
    }

    public void performAction(InfoPack[] methodParameters) throws InvalidChoiceException, SelectionNeededException, GameOverException {
        String cardName = methodParameters[0].getCharacter().getName();
        if(methodParameters[0].getSide() != game.getTurn() && !(methodParameters[0].getCharacter() instanceof Passive)){
            throw new InvalidChoiceException();
        }
        try{
            if(!controlVersion(methodParameters[1]) || !methodParameters[1].isOnGround()){
                throw new InvalidChoiceException();
            }
        }catch (ArrayIndexOutOfBoundsException e){}
        try {
            boolean check = false;
            for(CardName annotation: methodMap.keySet()){
                if(annotation.value().equals(cardName) && annotation.isForOnBoard() == methodParameters[0].isOnGround()){
                    methodMap.get(annotation).invoke(this, methodParameters);
                    check =true;
                }
            }
            if(!check){
                throw new InvalidChoiceException();
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            exceptionCheck(e);
            if(methodParameters[0].getCharacter() instanceof Minion && methodParameters[0].isOnGround()){
                Minion minion = (Minion) methodParameters[0].getCharacter();
                if(!minion.isCharge() && !minion.isRush()) throw new InvalidChoiceException();
            }
            if(methodParameters[0].getCharacter() instanceof Weapon && methodParameters[0].isOnGround()){
                Weapon weapon = (Weapon) methodParameters[0].getCharacter();
                if(!weapon.isCharge()) throw new InvalidChoiceException();
            }
            if(methodParameters[0].getCharacter() instanceof HeroPower){
                HeroPower heroPower = (HeroPower) methodParameters[0].getCharacter();
                if(!heroPower.isCharge()) throw new InvalidChoiceException();
            }
            for(SelectAction annotation: selectActionMethodMap.keySet()){
                if(annotation.value().equals(cardName) && annotation.isForOnBoard() == methodParameters[0].isOnGround()){
                    try {
                        selectActionMethodMap.get(annotation).invoke(this, methodParameters);
                    } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException o) {
                        exceptionCheck(o);
                    }
                    return;
                }
            }
            throw new SelectionNeededException();
        }
    }

    private void exceptionCheck(Exception e) throws InvalidChoiceException, SelectionNeededException, GameOverException {
        if(e.getCause() instanceof InvalidChoiceException){
            throw (InvalidChoiceException) e.getCause();
        }
        if(e.getCause() instanceof SelectionNeededException){
            throw (SelectionNeededException) e.getCause();
        }
        if (e.getCause() instanceof GameOverException){
            throw (GameOverException) e.getCause();
        }
    }

    private boolean controlVersion(InfoPack infoPack){
        return (infoPack.getCharacter() instanceof Minion || infoPack.getCharacter() instanceof Hero) && infoPack.isOnGround();
    }

    private void attackCheck(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException {
        Character character1 = infoPack1.getCharacter();
        Character character2 = infoPack2.getCharacter();
        if(!isEnemy(infoPack1.getSide(), infoPack2.getSide())){
            throw new InvalidChoiceException();
        }
        if(character1 instanceof Minion){
            if(character2 instanceof Minion){
                if((!((Minion) character1).isCharge() && !((Minion) character1).isRush())){
                    throw new InvalidChoiceException();
                }
            }
            else {
                if(!((Minion) character1).isCharge()){
                    throw new InvalidChoiceException();
                }
            }
        }
        else {
            if(!((Weapon) character1).isCharge()){
                throw new InvalidChoiceException();
            }
        }
        tauntCheck(infoPack2);
    }

    private void tauntCheck(InfoPack infoPack) throws InvalidChoiceException {
        for(Minion minion: game.getCompetitor(infoPack.getSide()).getOnBoardCards()){
            if(minion.isTaunt() && !minion.isStealth() && !((Minion) infoPack.getCharacter()).isTaunt())
                throw new InvalidChoiceException();
        }
    }

    private void runAttackActions(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException {
        for(ActionHandler actionHandler: attackActionHandlers){
            if(actionHandler instanceof PerformActionHandler){
                InfoPack[] infoPacks = {infoPack1, infoPack2};
                ((PerformActionHandler) actionHandler).runAction(infoPacks);
            }
            try {
                actionHandler.runAction();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void attack(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        Character character1 = infoPack1.getCharacter();
        Character character2 = infoPack2.getCharacter();
        attackCheck(infoPack1, infoPack2);
        runAttackActions(infoPack1,infoPack2);
        if(character1 instanceof Minion){
            if(character2 instanceof Minion){
                ((Minion) character1).getDamage(((Minion) character2).getAttack());
                ((Minion) character2).getDamage(((Minion) character1).getAttack());
            }
            else {
                ((Hero) character2).getDamage(((Minion) character1).getAttack());
            }
            ((Minion) character1).setCharge(false);
            ((Minion) character1).setRush(false);
            ((Minion) character1).setStealth(false);
        }
        else {
            if (character2 instanceof Minion){
                game.getCompetitor(game.getTurn()).getHero().getDamage(((Minion) character2).getAttack());
                ((Minion) character2).getDamage(((Weapon) character1).getAttack());
            }
            else {
                ((Hero) character2).getDamage(((Weapon) character1).getAttack());
            }
            ((Weapon) character1).setCharge(false);
            ((Weapon) character1).setDurability(((Weapon) character1).getDurability() - 1);
        }
    }

    private boolean isEnemy(int side1, int side2){
        if(side1 == side2){
            return false;
        }
        return true;
    }

    @CardName(value = "Arena Patron", isForOnBoard = true)
    public void action1(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
        if(infoPack2.getCharacter() instanceof Minion && ((Minion) infoPack2.getCharacter()).getHp() < 0){
            game.getActionRequest().getSummonMinion().execute(DataManager.getInstance().getObject(Minion.class, infoPack1.getCharacter().getName()), infoPack1.getSide(), -1);
        }
    }

    @CardName(value = "Arena Patron", isForOnBoard = false)
    public void action1(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Argent Commander", isForOnBoard = true)
    public void action2(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Argent Commander", isForOnBoard = false)
    public void action2(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Bluegill Warrior", isForOnBoard = true)
    public void action3(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Bluegill Warrior", isForOnBoard = false)
    public void action3(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Curio Collector", isForOnBoard = true)
    public void action4(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }


    @CardName(value = "Curio Collector", isForOnBoard = false)
    public void action5(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        game.getActionRequest().getDrawCard().addAction(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                if(((Minion) infoPack.getCharacter()).getHp() > 0 && game.getTurn() == infoPack.getSide()){
                    ((Minion) infoPack.getCharacter()).setHp(((Minion) infoPack.getCharacter()).getHp() + 1);
                    ((Minion) infoPack.getCharacter()).setAttack(((Minion) infoPack.getCharacter()).getAttack() + 1);
                }
            }
        });
    }

    @CardName(value = "Deathwing", isForOnBoard = true)
    public void action6(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Deathwing", isForOnBoard = false)
    public void action7(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getCompetitor(0).getOnBoardCards().clear();
        game.getCompetitor(1).getOnBoardCards().clear();
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Drakkari Trickster", isForOnBoard = true)
    public void action7(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Drakkari Trickster", isForOnBoard = false)
    public void action8(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        Competitor competitor = game.getCompetitor(infoPack.getSide());
        try {
            if(competitor.getInDeckCards().size() > 0){
                competitor.drawCard();
                competitor.addCardInDeck(DataManager.getInstance().getObject(Card.class, competitor.getInHandCards().get(competitor.getInHandCards().size() - 1).getName()));
            }
        } catch (Exception e) { }
    }

    @CardName(value = "Dreadscale", isForOnBoard = true)
    public void action9(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Dreadscale", isForOnBoard = false)
    public void action10(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        game.getActionRequest().getEndTurn().addAction(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                if(((Minion) infoPack.getCharacter()).getHp() > 0 && game.getTurn() != infoPack.getSide()){
                    ArrayList<Card> cards = new ArrayList<>();
                    cards.addAll(game.getCompetitor(0).getOnBoardCards());
                    cards.addAll(game.getCompetitor(1).getOnBoardCards());
                    for(Card card: cards){
                        if(card instanceof Minion && card != infoPack.getCharacter()){
                            ((Minion) card).getDamage(1);
                        }
                    }
                }
            }
        });
    }

    @CardName(value = "Gruul", isForOnBoard = true)
    public void action11(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Gruul", isForOnBoard = false)
    public void action12(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        game.getActionRequest().getEndTurn().addAction(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                if(((Minion) infoPack.getCharacter()).getHp() > 0) {
                    ((Minion) infoPack.getCharacter()).setAttack(((Minion) infoPack.getCharacter()).getAttack() + 1);
                    ((Minion) infoPack.getCharacter()).setHp(((Minion) infoPack.getCharacter()).getHp() + 1);
                }
            }
        });
    }

    @CardName(value = "High Priest Amet", isForOnBoard = true)
    public void action13(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "High Priest Amet", isForOnBoard = false)
    public void action14(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        game.getActionRequest().getSummonMinion().addAction(new PlayActionHandler() {
            @Override
            public void runAction(Card card, int side) throws Exception {
                if(((Minion) infoPack.getCharacter()).getHp() > 0 && side == infoPack.getSide()){
                    ((Minion) card).setHp(((Minion) infoPack.getCharacter()).getHp());
                }
            }
            @Override
            public void runAction(){ }
        });
    }

    @CardName(value = "Phantom Militia", isForOnBoard = true)
    public void action15(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Phantom Militia", isForOnBoard = false)
    public void action16(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        Card card = DataManager.getInstance().getObject(Minion.class, infoPack.getCharacter().getName());
        game.getCompetitor(infoPack.getSide()).addCardInHand(card);
        game.getActionRequest().getEndTurn().addAction(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                game.getCompetitor(infoPack.getSide()).removeCardFromHand(card);
            }
        });
    }

    @CardName(value = "Psyali   chic Conjurer", isForOnBoard = true)
    public void action16(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Psychic Conjurer", isForOnBoard = false)
    public void action17(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        ArrayList<Card> cards = game.getCompetitor((infoPack.getSide()+1)%2).getInDeckCards();
        if(cards.size() > 0){
            Random random = new Random();
            game.getCompetitor(infoPack.getSide()).addCardInHand(DataManager.getInstance().getObject(Card.class, cards.get(random.nextInt(cards.size())).getName()));
        }
    }

    @CardName(value = "Sathrovarr", isForOnBoard = true)
    public void action18(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Sathrovarr", isForOnBoard = false)
    public void action19(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        if(infoPack1.getSide() != infoPack2.getSide() || !infoPack2.isOnGround() || !(infoPack2.getCharacter() instanceof Minion)){
            throw new InvalidChoiceException();
        }
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack1.getCharacter(), infoPack1.getSide(), infoPack1.getSummonPlace());
        Card[] cards = new Card[3];
        for(int i = 0 ; i < 3; i++){
            cards[i] = DataManager.getInstance().getObject(Minion.class, infoPack2.getCharacter().getName());
        }
        Competitor competitor = game.getCompetitor(infoPack1.getSide());
        competitor.addCardInDeck(cards[0]);
        competitor.addCardInHand(cards[1]);
        game.getActionRequest().getSummonMinion().execute(cards[2], infoPack1.getSide(), -1);
    }

    @SelectAction(value = "Sathrovarr", isForOnBoard = false)
    public void action20(InfoPack infoPack) throws SelectionNeededException, GameOverException, InvalidChoiceException {
        if(game.getCompetitor(game.getTurn()).getOnBoardCards().size() != 0){
            throw new SelectionNeededException();
        }
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Security Rover", isForOnBoard = true)
    public void action21(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Security Rover", isForOnBoard = false)
    public void action22(InfoPack infoPack) throws InvalidChoiceException, GameOverException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        ((Minion) infoPack.getCharacter()).addActionForDamage(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                Minion minion = DataManager.getInstance().getObject(Minion.class, "Mech");
                minion.setHp(2);
                minion.setAttack(3);
                minion.setTaunt(true);
                game.getActionRequest().getSummonMinion().execute(minion, infoPack.getSide(), -1);
            }
        });
    }

    @CardName(value = "Sen'jin Shieldmasta", isForOnBoard = true)
    public void action23(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Sen'jin Shieldmasta", isForOnBoard = false)
    public void action23(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Stormwind Champion", isForOnBoard = true)
    public void action24(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Stormwind Champion", isForOnBoard = false)
    public void action25(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        for(Minion minion: game.getCompetitor(infoPack.getSide()).getOnBoardCards()){
            minion.setHp(minion.getHp() + 1);
            minion.setAttack(minion.getAttack() + 1);
        }
    }

    @CardName(value = "Tomb Warden", isForOnBoard = true)
    public void action26(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Tomb Warden", isForOnBoard = false)
    public void action27(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        game.getActionRequest().getSummonMinion().execute(DataManager.getInstance().getObject(Minion.class, infoPack.getCharacter().getName()), infoPack.getSide(), -1);
    }

    @CardName(value = "Tortollan Forager", isForOnBoard = true)
    public void action28(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Tortollan Forager", isForOnBoard = false)
    public void action29(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        ArrayList<Minion> minions = DataManager.getInstance().getAllCharacter(Minion.class);
        Random random = new Random();
        while (true){
            Minion minion = minions.get(random.nextInt(minions.size()));
            if (minion.getAttack() >= 5){
                game.getCompetitor(infoPack.getSide()).addCardInHand(minion);
                break;
            }
        }
    }

    @CardName(value = "Voodoo Doctor", isForOnBoard = true)
    public void action30(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Voodoo Doctor", isForOnBoard = false)
    public void action31(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
        restoreHealth(game.getCompetitor(infoPack.getSide()).getHero(), game.getCompetitor(infoPack.getSide()).getHero(),2);
    }

    @CardName(value = "Wisp", isForOnBoard = true)
    public void action32(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Wisp", isForOnBoard = false)
    public void action32(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Blizzard", isForOnBoard = false)
    public void action33(InfoPack infoPack){
        for(Minion minion: game.getCompetitor((infoPack.getSide()+1)%2).getOnBoardCards()){
            minion.getDamage(2);
        }
    }

    @CardName(value = "Book of Specters", isForOnBoard = false)
    public void action34(InfoPack infoPack) throws GameOverException {
        for(int i = 0; i < 3; i++){
            if(game.getCompetitor(infoPack.getSide()).getInDeckCards().size() == 0) break;
            game.getActionRequest().getDrawCard().execute();
            ArrayList<Card> hand = game.getCompetitor(infoPack.getSide()).getInHandCards();
            if(hand.size() > 0 && hand.get(hand.size() - 1) instanceof Spell){
                hand.remove(hand.get(hand.size() - 1));
                game.getActionRequest().reduceDrawNumber(1);
            }
        }
    }

    @CardName(value = "Friendly Smith", isForOnBoard = false)
    public void action35(InfoPack infoPack){
        Random random = new Random();
        ArrayList<Weapon> weapons = DataManager.getInstance().getAllCharacter(Weapon.class);
        Weapon weapon = weapons.get(random.nextInt(weapons.size()));
        weapon.setDurability(weapon.getDurability() + 2);
        weapon.setAttack(weapon.getAttack() + 2);
        game.getCompetitor(infoPack.getSide()).addCardInDeck(weapon);
    }

    @CardName(value = "Gnomish Army Knife", isForOnBoard = false)
    public void action36(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException {
        if(!(infoPack2.getCharacter()instanceof Minion) || !infoPack2.isOnGround()){
            throw new InvalidChoiceException();
        }
        Minion minion = (Minion) infoPack2.getCharacter();
        minion.setTaunt(true);
        minion.setDivineShield(true);
        minion.setCharge(true);
        minion.setStealth(true);
        final int[] cnt = {0};
        game.getActionRequest().getPerformAction().addAction(new PerformActionHandler() {
            @Override
            public void runAction(InfoPack[] infoPacks) {
                if(infoPacks[0].getCharacter() == minion && cnt[0] < 1){
                    minion.setCharge(true);
                    cnt[0]++;
                }
                Hero hero = game.getCompetitor(infoPack2.getSide()).getHero();
                hero.setHp(hero.getHp() + minion.getAttack());
            }

            @Override
            public void runAction() throws Exception {

            }
        });
        game.getActionRequest().getEndTurn().addAction(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                cnt[0] = 0;
            }
        });
    }

    @CardName(value = "Mortal Coil", isForOnBoard = false)
    public void action37(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        if(!(infoPack2.getCharacter() instanceof Minion) || !infoPack2.isOnGround()){
            throw new InvalidChoiceException();
        }
        ((Minion) infoPack2.getCharacter()).getDamage(1);
        if(((Minion) infoPack2.getCharacter()).getHp() <= 0){
            game.getActionRequest().getDrawCard().execute();
        }
    }

    @CardName(value = "Pharaoh's Blessing", isForOnBoard = false)
    public void action38(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException {
        if(!(infoPack2.getCharacter() instanceof Minion) || !infoPack2.isOnGround()){
            throw new InvalidChoiceException();
        }
        Minion minion = (Minion) infoPack2.getCharacter();
        minion.setDivineShield(true);
        minion.setTaunt(true);
        minion.setHp(minion.getHp() + 4);
        minion.setAttack(minion.getAttack() + 4);
    }

    @CardName(value = "Polymorph", isForOnBoard = false)
    public void action39(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException {
        if(!(infoPack2.getCharacter() instanceof Minion) || !infoPack2.isOnGround()){
            throw new InvalidChoiceException();
        }
        Minion minion = (Minion) infoPack2.getCharacter();
        minion.setName("Sheep");
        minion.setDescription("");
        minion.setAttack(1);
        minion.setHp(1);
        minion.setTaunt(false);
        minion.setDivineShield(false);
        minion.setStealth(false);
    }

    @CardName(value = "Sprint", isForOnBoard = false)
    public void action40(InfoPack infoPack) throws GameOverException {
        for(int i = 0; i < 4; i++){
            game.getActionRequest().getDrawCard().execute();
        }
    }

    @CardName(value = "Swarm of locusts", isForOnBoard = false)
    public void action41(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        for(int i = 0; i < 7; i++){
            game.getActionRequest().getSummonMinion().execute(DataManager.getInstance().getObject(Minion.class, "Locust"), infoPack.getSide(), -1);
        }
    }

    @CardName(value = "WANTED!", isForOnBoard = false)
    public void action42(InfoPack infoPack) {
        Random random = new Random();
        ArrayList<Minion> minions = game.getCompetitor((infoPack.getSide()+1)%2).getOnBoardCards();
        if(minions.size() > 0 ){
            minions.get(random.nextInt(minions.size())).getDamage(3);
        }
    }

    @CardName(value = "Assassin's Blade", isForOnBoard = true)
    public void action43(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Assassin's Blade", isForOnBoard = false)
    public void action44(InfoPack infoPack) {
        game.getCompetitor(infoPack.getSide()).setHeroWeapon((Weapon) infoPack.getCharacter());
    }

    @CardName(value = "Blood Fury", isForOnBoard = true)
    public void action45(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Blood Fury", isForOnBoard = false)
    public void action46(InfoPack infoPack) {
        game.getCompetitor(infoPack.getSide()).setHeroWeapon((Weapon) infoPack.getCharacter());
    }

    @CardName(value = "Bloodclaw", isForOnBoard = true)
    public void action47(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Bloodclaw", isForOnBoard = false)
    public void action48(InfoPack infoPack) throws GameOverException {
        game.getCompetitor(infoPack.getSide()).setHeroWeapon((Weapon) infoPack.getCharacter());
        game.getCompetitor(infoPack.getSide()).getHero().getDamage(5);
    }

    @CardName(value = "Dragon Claw", isForOnBoard = true)
    public void action49(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Dragon Claw", isForOnBoard = false)
    public void action50(InfoPack infoPack) {
        game.getCompetitor(infoPack.getSide()).setHeroWeapon((Weapon) infoPack.getCharacter());
    }

    @CardName(value = "Heavy Axe", isForOnBoard = true)
    public void action51(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Heavy Axe", isForOnBoard = false)
    public void action52(InfoPack infoPack) {
        game.getCompetitor(infoPack.getSide()).setHeroWeapon((Weapon) infoPack.getCharacter());
    }

    @CardName(value = "Sheep", isForOnBoard = true)
    public void action53(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Sheep", isForOnBoard = false)
    public void action54(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Card) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Locust", isForOnBoard = true)
    public void action55(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Locust", isForOnBoard = false)
    public void action56(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Card) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

    @CardName(value = "Activate the Obelisk", isForOnBoard = false)
    public void action57(InfoPack infoPack){
        int mana = game.getCompetitor(infoPack.getSide()).getSpentManaOnClass(Weapon.class);
        game.getCompetitor(infoPack.getSide()).addQuest((Quest) infoPack.getCharacter(), new QuestActionHandler() {
            @Override
            public double getQuestPercent() {
                return (game.getCompetitor(infoPack.getSide()).getSpentManaOnClass(Weapon.class)-mana)/6.0;
            }

            @Override
            public void runAction() throws Exception {
                game.getCompetitor((infoPack.getSide()+1)%2).getInHandCards().clear();
            }
        });
    }

    @CardName(value = "Learn Draconic", isForOnBoard = false)
    public void action58(InfoPack infoPack){
        int mana = game.getCompetitor(infoPack.getSide()).getSpentManaOnClass(Spell.class);
        game.getCompetitor(infoPack.getSide()).addQuest((Quest) infoPack.getCharacter(), new QuestActionHandler() {
            @Override
            public double getQuestPercent() {
                return (game.getCompetitor(infoPack.getSide()).getSpentManaOnClass(Spell.class)-mana)/8.0;
            }

            @Override
            public void runAction() throws Exception {
                Minion minion = DataManager.getInstance().getObject(Minion.class, "Deathwing");
                minion.setHp(6);
                minion.setAttack(6);
                game.getActionRequest().getSummonMinion().execute(minion, infoPack.getSide(), -1);
            }
        });
    }

    @CardName(value = "Strength in Numbers", isForOnBoard = false)
    public void action59(InfoPack infoPack){
        int mana = game.getCompetitor(infoPack.getSide()).getSpentManaOnClass(Minion.class);
        game.getCompetitor(infoPack.getSide()).addQuest((Quest) infoPack.getCharacter(), new QuestActionHandler() {
            @Override
            public double getQuestPercent() {
                return (game.getCompetitor(infoPack.getSide()).getSpentManaOnClass(Minion.class)-mana)/10.0;
            }

            @Override
            public void runAction() throws Exception {
                ArrayList<Minion> minions = new ArrayList<>();
                for(Card card: game.getCompetitor(infoPack.getSide()).getInDeckCards()){
                    if(card instanceof Minion){
                        minions.add((Minion) card);
                    }
                }
                if (minions.size() == 0) return;
                Random random = new Random();
                game.getActionRequest().getSummonMinion().execute(
                        DataManager.getInstance().getObject(Minion.class, minions.get(random.nextInt(minions.size())).getName()),
                        infoPack.getSide(),
                        -1
                );
            }
        });
    }

    @CardName(value = "Mana jump", isForOnBoard = false)
    public void action60(InfoPack infoPack){
        game.getCompetitor(infoPack.getSide()).setFullMana(game.getCompetitor(infoPack.getSide()).getFullMana() + 1);
        game.getCompetitor(infoPack.getSide()).setLeftMana(game.getCompetitor(infoPack.getSide()).getLeftMana() + 1);
    }

    @CardName(value = "Nurse", isForOnBoard = false)
    public void action61(InfoPack infoPack){
        game.getActionRequest().getEndTurn().addAction(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                if(game.getTurn() != infoPack.getSide()){
                    ArrayList<Minion> minions = new ArrayList<>();
                    for(Minion minion: game.getCompetitor(infoPack.getSide()).getOnBoardCards()){
                        if(minion.getHp() < DataManager.getInstance().getObject(Minion.class, minion.getName()).getHp()){
                            minions.add(minion);
                        }
                    }
                    Random random = new Random();
                    if(minions.size() > 0) {
                        Minion minion = minions.get(random.nextInt(minions.size()));
                        restoreHealth(minion,
                                game.getCompetitor(infoPack.getSide()).getHero(),
                                DataManager.getInstance().getObject(Minion.class, minion.getName()).getHp()
                        );
                    }
                }
            }
        });
    }

    @CardName(value = "Off Cards", isForOnBoard = false)
    public void action62(InfoPack infoPack){
        CardAction cardAction = new CardAction() {
            @Override
            public void runAction(Card card) {
                card.setMana(Math.max(0, card.getMana() - 1));
            }
            @Override
            public void runAction() throws Exception {}
        };
        for(Card card: game.getCompetitor(infoPack.getSide()).getInHandCards()){
            cardAction.runAction(card);
        }
        for(Card card: game.getCompetitor(infoPack.getSide()).getInDeckCards()){
            cardAction.runAction(card);
        }
        game.getCompetitor(infoPack.getSide()).addDeckAddActions(cardAction);
        game.getCompetitor(infoPack.getSide()).addHandAddActions(cardAction);
    }

    @CardName(value = "Twice Draw", isForOnBoard = false)
    public void action63(InfoPack infoPack){
        game.getCompetitor(infoPack.getSide()).setDrawNumber(game.getCompetitor(infoPack.getSide()).getDrawNumber() + 1);
    }

    @CardName(value = "Free Power", isForOnBoard = false)
    public void action64(InfoPack infoPack){
        HeroPower heroPower = game.getCompetitor(infoPack.getSide()).getHero().getHeroPower();
        heroPower.setMana(Math.max(heroPower.getMana() - 1, 0));
        final boolean[] used = {false};
        game.getActionRequest().getPerformAction().addAction(new PerformActionHandler() {
            @Override
            public void runAction(InfoPack[] infoPacks) throws InvalidChoiceException {
                if(infoPacks[0].getCharacter() instanceof HeroPower && !used[0]){
                    used[0] = true;
                    ((HeroPower) infoPacks[0].getCharacter()).setCharge(true);
                }
            }

            @Override
            public void runAction() throws Exception { }
        });
        game.getActionRequest().getEndTurn().addAction(new ActionHandler() {
            @Override
            public void runAction() throws Exception {
                used[0] = false;
            }
        });
    }

    private void attackWithSpell(InfoPack infoPack, int damage) throws InvalidChoiceException, GameOverException {
        if(!infoPack.isOnGround() || (!(infoPack.getCharacter() instanceof Minion) && !(infoPack.getCharacter() instanceof Hero))){
            throw new InvalidChoiceException();
        }
        if(infoPack.getCharacter() instanceof Minion){
            if(((Minion)infoPack.getCharacter()).isStealth()){
                throw new InvalidChoiceException();
            }
            ((Minion) infoPack.getCharacter()).getDamage(damage);
        }
        else {
            ((Hero) infoPack.getCharacter()).getDamage(damage);
        }
    }

    private void restoreHealth(Character character, Hero ownerHero, int health){
        if (ownerHero.getName().equals("Priest")) health *= 2;
        if(character instanceof Hero){
            Hero hero = (Hero) character;
            hero.setHp(Math.max(hero.getHp(),Math.min(hero.getHp() + health, DataManager.getInstance().getObject(Hero.class, hero.getName()).getHp())));
        }
        if(character instanceof Minion){
            Minion minion = (Minion) character;
            minion.setHp(Math.max(minion.getHp(), Math.min(minion.getHp() + health, DataManager.getInstance().getObject(Minion.class, minion.getName()).getHp())));
        }
    }

    @CardName(value = "MageHeroPower", isForOnBoard = true)
    public void action65(InfoPack infoPack1, InfoPack infoPack2) throws GameOverException, InvalidChoiceException {
        game.getCompetitor(infoPack1.getSide()).useHeroPower();
        attackWithSpell(infoPack2, 1);
    }

    @CardName(value = "PaladinHeroPower", isForOnBoard = true)
    public void action66(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getCompetitor(infoPack.getSide()).useHeroPower();
        game.getActionRequest().getSummonMinion().execute(DataManager.getInstance().getObject(Minion.class, "Sheep"), infoPack.getSide(), -1);
        game.getActionRequest().getSummonMinion().execute(DataManager.getInstance().getObject(Minion.class, "Sheep"), infoPack.getSide(), -1);
    }

    @CardName(value = "PriestHeroPower", isForOnBoard = true)
    public void action67(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException {
        if(!infoPack2.isOnGround() || (!(infoPack2.getCharacter() instanceof Minion) && !(infoPack2.getCharacter() instanceof Hero))){
            throw new InvalidChoiceException();
        }
        game.getCompetitor(infoPack1.getSide()).useHeroPower();
        restoreHealth(infoPack2.getCharacter(), game.getCompetitor(infoPack1.getSide()).getHero(),2);
    }

    @CardName(value = "RogueHeroPower", isForOnBoard = true)
    public void action68(InfoPack infoPack) throws InvalidChoiceException {
        game.getCompetitor(infoPack.getSide()).useHeroPower();
        Competitor enemy = game.getCompetitor((infoPack.getSide()+1)%2);
        Random random = new Random();
        if(enemy.getInDeckCards().size() > 0){
            Card card = enemy.getInDeckCards().get(random.nextInt(enemy.getInDeckCards().size()));
            game.getCompetitor(infoPack.getSide()).addCardInHand(DataManager.getInstance().getObject(Card.class,card.getName()));
            enemy.removeCardFromDeck(card);
        }
        if(game.getCompetitor(infoPack.getSide()).getHeroWeapon() != null && enemy.getInHandCards().size() > 0){
            Card card = enemy.getInHandCards().get(random.nextInt(enemy.getInHandCards().size()));
            game.getCompetitor(infoPack.getSide()).addCardInHand(DataManager.getInstance().getObject(Card.class,card.getName()));
            enemy.removeCardFromHand(card);
        }
    }

    @CardName(value = "WarlockHeroPower", isForOnBoard = true)
    public void action69(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getCompetitor(infoPack.getSide()).useHeroPower();
        game.getCompetitor(infoPack.getSide()).getHero().getDamage(2);
        Random random = new Random();
        ArrayList<Minion> minions = game.getCompetitor(infoPack.getSide()).getOnBoardCards();
        if(random.nextInt(2) == 0 && minions.size() > 0){
            Minion minion = minions.get(random.nextInt(minions.size()));
            minion.setAttack(minion.getAttack() + 1);
            minion.setHp(minion.getHp() + 1);
        }
        else{
            game.getActionRequest().getDrawCard().execute();
        }
    }

    @CardName(value = "Mech", isForOnBoard = true)
    public void action70(InfoPack infoPack1, InfoPack infoPack2) throws InvalidChoiceException, GameOverException {
        attack(infoPack1, infoPack2);
    }

    @CardName(value = "Mech", isForOnBoard = false)
    public void action71(InfoPack infoPack) throws GameOverException, InvalidChoiceException {
        game.getActionRequest().getSummonMinion().execute((Minion) infoPack.getCharacter(), infoPack.getSide(), infoPack.getSummonPlace());
    }

}
