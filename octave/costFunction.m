function [J, grad] = costFunction(t, x, y)
	m = length(y);
	r = hyprb(t,x)-y;
	J=1/(2*m)*(r'*r);
	gm = [ones(m,1), 1./(x+t(3)), -t(2)./(x+t(3)).^2];
	grad=(1/m)*gm'*r;
end
